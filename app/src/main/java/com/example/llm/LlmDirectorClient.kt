package com.example.llm

import com.example.data.BuiltInPrompts
import com.example.model.AnimationStyle
import com.example.model.SceneScript
import com.example.model.StudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LlmScriptResult(
    val scenes: List<SceneScript>,
    val providerUsed: String
)

class LlmDirectorClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateStoryScript(
        topic: String,
        durationSeconds: Int,
        style: AnimationStyle,
        settings: StudioSettings,
        onStatus: (String) -> Unit
    ): LlmScriptResult = withContext(Dispatchers.IO) {
        val activeCreativePrompt = settings.activeTemplate?.content
            ?: BuiltInPrompts.VIRAL_PINOY_EXPLAINER.content
        val systemPrompt = BuiltInPrompts.composeSystemPrompt(
            activeCreativePrompt = activeCreativePrompt,
            durationSeconds = durationSeconds,
            style = style
        )
        val userPrompt = buildString {
            append("Topic: $topic\n")
            append("Animation Style: ${style.displayName}\n")
            append("Target Duration: ${durationSeconds} seconds\n")
            append("Write the Tagalog scenes JSON array now.")
        }

        val errors = mutableListOf<String>()

        // 1. Custom OpenAI-Compatible Endpoint (optional)
        if (settings.customBaseUrl.isNotBlank() && settings.customModel.isNotBlank()) {
            val base = settings.customBaseUrl.trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val result = tryOpenAiWithRetry(
                providerName = "Custom (${settings.customModel})",
                url = url,
                model = settings.customModel,
                apiKey = settings.customApiKey,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                onStatus = onStatus
            )
            if (result.isSuccess) return@withContext result.getOrThrow()
            errors.add("Custom: ${result.exceptionOrNull()?.message}")
        }

        // 2. Groq Free API
        if (settings.groqApiKey.isNotBlank()) {
            val groqModels = listOf("qwen/qwen3-32b", "openai/gpt-oss-120b")
            for (model in groqModels) {
                val result = tryOpenAiWithRetry(
                    providerName = "Groq ($model)",
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    model = model,
                    apiKey = settings.groqApiKey,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    onStatus = onStatus
                )
                if (result.isSuccess) return@withContext result.getOrThrow()
                errors.add("Groq[$model]: ${result.exceptionOrNull()?.message}")
            }
        }

        // 3. OpenRouter Free API
        if (settings.openRouterApiKey.isNotBlank()) {
            val openRouterModels = listOf("qwen/qwen3-235b-a22b:free", "deepseek/deepseek-r1:free")
            for (model in openRouterModels) {
                val result = tryOpenAiWithRetry(
                    providerName = "OpenRouter ($model)",
                    url = "https://openrouter.ai/api/v1/chat/completions",
                    model = model,
                    apiKey = settings.openRouterApiKey,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    extraHeaders = mapOf(
                        "HTTP-Referer" to "https://toonforge.tagalog.app",
                        "X-Title" to "ToonForge Tagalog"
                    ),
                    onStatus = onStatus
                )
                if (result.isSuccess) return@withContext result.getOrThrow()
                errors.add("OpenRouter[$model]: ${result.exceptionOrNull()?.message}")
            }
        }

        // 4. Keyless Unlimited Default: AI Horde Text API
        val hordeResult = tryAiHordeTextWithRetry(
            apiKey = settings.aiHordeKey.ifBlank { "0000000000" },
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            onStatus = onStatus
        )
        if (hordeResult.isSuccess) return@withContext hordeResult.getOrThrow()
        errors.add("AI Horde: ${hordeResult.exceptionOrNull()?.message}")

        throw IOException("All LLM providers failed: ${errors.joinToString(" | ")}")
    }

    private suspend fun tryOpenAiWithRetry(
        providerName: String,
        url: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        extraHeaders: Map<String, String> = emptyMap(),
        onStatus: (String) -> Unit
    ): Result<LlmScriptResult> {
        var lastError: Throwable? = null
        for (attempt in 1..3) { // initial + up to 2 retries on malformed output
            try {
                onStatus("Writing Tagalog script via $providerName (try $attempt/3)...")
                val retryHint = if (attempt > 1) {
                    "\nCRITICAL: Previous output was malformed JSON. Output ONLY a raw JSON array starting with '[' and ending with ']'."
                } else ""

                val payload = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.7)
                    put("max_tokens", 2800)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt + retryHint)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    })
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $apiKey")
                }
                extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

                val rawText = httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP ${resp.code}: ${bodyStr.take(240)}")
                    }
                    val root = JSONObject(bodyStr)
                    root.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }

                val scenes = JsonSanitizer.parseAndValidateScenes(rawText)
                return Result.success(LlmScriptResult(scenes = scenes, providerUsed = providerName))
            } catch (e: MalformedSceneJsonException) {
                lastError = e
                delay(400)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
        return Result.failure(lastError ?: IOException("Malformed JSON after retries"))
    }

    private suspend fun tryAiHordeTextWithRetry(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        onStatus: (String) -> Unit
    ): Result<LlmScriptResult> {
        var lastError: Throwable? = null
        for (attempt in 1..3) {
            try {
                onStatus("Writing Tagalog script via AI Horde (free keyless, try $attempt/3)...")
                val retryHint = if (attempt > 1) {
                    "\nOutput ONLY a valid JSON array starting with '[' and ending with ']'."
                } else ""

                val combinedPrompt = buildString {
                    append("<|system|>\n")
                    append(systemPrompt)
                    append(retryHint)
                    append("\n<|user|>\n")
                    append(userPrompt)
                    append("\n<|assistant|>\n[")
                }

                val payload = JSONObject().apply {
                    put("prompt", combinedPrompt)
                    put("params", JSONObject().apply {
                        put("max_context_length", 4096)
                        put("max_length", 1800)
                        put("temperature", 0.65)
                        put("top_p", 0.9)
                    })
                }

                val submitReq = Request.Builder()
                    .url("https://aihorde.net/api/v2/generate/text/async")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("apikey", apiKey)
                    .header("Client-Agent", "ToonForgeTagalog:1.0:android")
                    .header("Content-Type", "application/json")
                    .build()

                val jobId = httpClient.newCall(submitReq).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IOException("AI Horde submit HTTP ${resp.code}: ${bodyStr.take(200)}")
                    }
                    JSONObject(bodyStr).getString("id")
                }

                var generatedText: String? = null
                val startWaitMs = System.currentTimeMillis()
                while (System.currentTimeMillis() - startWaitMs < 90_000L) {
                    delay(2_500L)
                    val statusReq = Request.Builder()
                        .url("https://aihorde.net/api/v2/generate/text/status/$jobId")
                        .get()
                        .header("Client-Agent", "ToonForgeTagalog:1.0:android")
                        .build()

                    val statusJson = httpClient.newCall(statusReq).execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw IOException("AI Horde status HTTP ${resp.code}")
                        }
                        JSONObject(bodyStr)
                    }

                    val done = statusJson.optBoolean("done", false)
                    val faulted = statusJson.optBoolean("faulted", false)
                    if (faulted) {
                        throw IOException("AI Horde text job faulted")
                    }
                    val waitTime = statusJson.optInt("wait_time", 0)
                    if (!done) {
                        onStatus("AI Horde queued (~${waitTime}s left)...")
                        continue
                    }

                    val gens = statusJson.optJSONArray("generations")
                    if (gens != null && gens.length() > 0) {
                        val rawGen = gens.getJSONObject(0).optString("text", "")
                        // Since we primed assistant with '[', prepend '[' if needed
                        generatedText = if (rawGen.trimStart().startsWith("[")) rawGen else "[$rawGen"
                        break
                    } else {
                        throw IOException("AI Horde completed with empty generations")
                    }
                }

                val text = generatedText ?: throw IOException("AI Horde text generation timed out")
                val scenes = JsonSanitizer.parseAndValidateScenes(text)
                return Result.success(LlmScriptResult(scenes = scenes, providerUsed = "AI Horde Text"))
            } catch (e: MalformedSceneJsonException) {
                lastError = e
                delay(500)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
        return Result.failure(lastError ?: IOException("AI Horde malformed JSON after retries"))
    }
}
