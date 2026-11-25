package com.example.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import com.example.model.AnimationStyle
import com.example.model.SceneScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class BackdropResult(
    val bitmap: Bitmap,
    val fromNetwork: Boolean
)

class AiHordeImageClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 16
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val POLL_TIMEOUT_MS = 90_000L
        private const val POLL_INTERVAL_MS = 2_400L
        private const val MAX_CONCURRENT_JOBS = 8
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateBackdropsBatch(
        scenes: List<SceneScript>,
        style: AnimationStyle,
        aiHordeApiKey: String,
        topicSeed: Int,
        onSceneCompleted: suspend (completedCount: Int, totalCount: Int) -> Unit = { _, _ -> }
    ): List<BackdropResult> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_JOBS)
        val completed = AtomicInteger(0)
        val total = scenes.size

        scenes.map { scene ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = generateBackdrop(
                        scenePrompt = scene.backgroundPrompt,
                        style = style,
                        aiHordeApiKey = aiHordeApiKey,
                        seed = scene.sceneId * 1013 + topicSeed
                    )
                    val count = completed.incrementAndGet()
                    onSceneCompleted(count, total)
                    result
                }
            }
        }.awaitAll()
    }

    suspend fun generateBackdrop(
        scenePrompt: String,
        style: AnimationStyle,
        aiHordeApiKey: String,
        seed: Int
    ): BackdropResult = withContext(Dispatchers.IO) {
        // Stickman Whiteboard skips all cloud image calls and draws offline paper grain locally
        if (!style.usesNetworkArt) {
            return@withContext BackdropResult(
                bitmap = ProceduralBackdropGenerator.generate(
                    prompt = scenePrompt,
                    style = style,
                    width = 1080,
                    height = 1920,
                    seed = seed
                ),
                fromNetwork = false
            )
        }

        val fullPrompt = "$scenePrompt, ${style.backdropPromptSuffix}"
        val decoded = tryFetchAiHordeImage(
            prompt = fullPrompt,
            apiKey = aiHordeApiKey.ifBlank { "0000000000" }
        )

        if (decoded != null) {
            val scaled = scaleCenterCrop(decoded, 1080, 1920)
            if (scaled !== decoded) decoded.recycle()
            return@withContext BackdropResult(bitmap = scaled, fromNetwork = true)
        }

        // Seamless zero-crash stylized 2.5D layered diorama fallback
        return@withContext BackdropResult(
            bitmap = ProceduralBackdropGenerator.generate(
                prompt = scenePrompt,
                style = style,
                width = 1080,
                height = 1920,
                seed = seed
            ),
            fromNetwork = false
        )
    }

    private suspend fun tryFetchAiHordeImage(prompt: String, apiKey: String): Bitmap? {
        return try {
            val payload = JSONObject().apply {
                put("prompt", prompt)
                put("params", JSONObject().apply {
                    put("width", 768)
                    put("height", 1344)
                    put("steps", 8)
                    put("n", 1)
                    put("sampler_name", "k_euler")
                })
                put("models", JSONArray().apply {
                    put("FLUX.1-schnell")
                    put("AlbedoBase XL (SDXL)")
                })
                put("nsfw", false)
                put("censor_nsfw", true)
                put("r2", true)
            }

            val submitReq = Request.Builder()
                .url("https://aihorde.net/api/v2/generate/async")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("apikey", apiKey)
                .header("Client-Agent", "ToonForgeTagalog:1.0:android")
                .header("Content-Type", "application/json")
                .build()

            val jobId = httpClient.newCall(submitReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optString("id").ifBlank { null }
            } ?: return null

            val startMs = System.currentTimeMillis()
            while (System.currentTimeMillis() - startMs < POLL_TIMEOUT_MS) {
                delay(POLL_INTERVAL_MS)
                val checkReq = Request.Builder()
                    .url("https://aihorde.net/api/v2/generate/check/$jobId")
                    .get()
                    .header("Client-Agent", "ToonForgeTagalog:1.0:android")
                    .build()

                val checkJson = httpClient.newCall(checkReq).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    JSONObject(resp.body?.string().orEmpty())
                }
                if (checkJson.optBoolean("faulted", false)) return null
                if (!checkJson.optBoolean("is_possible", true)) return null
                if (checkJson.optBoolean("done", false)) break
            }

            val statusReq = Request.Builder()
                .url("https://aihorde.net/api/v2/generate/status/$jobId")
                .get()
                .header("Client-Agent", "ToonForgeTagalog:1.0:android")
                .build()

            val imgRef = httpClient.newCall(statusReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val statusJson = JSONObject(resp.body?.string().orEmpty())
                if (!statusJson.optBoolean("done", false)) return null
                val gens = statusJson.optJSONArray("generations") ?: return null
                if (gens.length() == 0) return null
                gens.getJSONObject(0).optString("img", "").ifBlank { null }
            } ?: return null

            val rawBytes: ByteArray = if (imgRef.startsWith("http://") || imgRef.startsWith("https://")) {
                val dlReq = Request.Builder().url(imgRef).get().build()
                httpClient.newCall(dlReq).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    resp.body?.bytes() ?: return null
                }
            } else {
                val b64 = imgRef.substringAfter("base64,", imgRef)
                Base64.decode(b64, Base64.DEFAULT)
            }

            // Strict Magic-Byte Validation (PNG / JPEG / WebP only)
            MagicByteValidator.validateAndDecode(rawBytes)
        } catch (_: Throwable) {
            null
        }
    }

    private fun scaleCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (src.width == targetW && src.height == targetH) return src
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        val dstRatio = targetW.toFloat() / targetH.toFloat()
        val srcRect = if (srcRatio > dstRatio) {
            val cropW = (src.height * dstRatio).toInt()
            val left = (src.width - cropW) / 2
            Rect(left, 0, left + cropW, src.height)
        } else {
            val cropH = (src.width / dstRatio).toInt()
            val top = (src.height - cropH) / 2
            Rect(0, top, src.width, top + cropH)
        }
        val dstRect = Rect(0, 0, targetW, targetH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, srcRect, dstRect, paint)
        return out
    }
}
