package com.example.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.example.model.ElevenLabsKeyEntry
import com.example.model.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class TtsSynthesisOutput(
    val audioBytes: ByteArray,
    val isWav: Boolean,
    val localWordTimings: List<WordTiming>,
    val keyUsed: String?
)

class ElevenLabsClient(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkSubscriptionQuota(apiKey: String): ElevenLabsKeyEntry = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/user/subscription")
            .get()
            .header("xi-api-key", apiKey.trim())
            .build()

        return@withContext try {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use ElevenLabsKeyEntry(
                        key = apiKey.trim(),
                        lastError = "HTTP ${resp.code}",
                        lastCheckedAtMs = System.currentTimeMillis()
                    )
                }
                val json = JSONObject(body)
                val used = json.optInt("character_count", 0)
                val limit = json.optInt("character_limit", 0)
                val tier = json.optString("tier", "free")
                ElevenLabsKeyEntry(
                    key = apiKey.trim(),
                    remainingChars = (limit - used).coerceAtLeast(0),
                    limitChars = limit,
                    tier = tier,
                    lastError = null,
                    lastCheckedAtMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            ElevenLabsKeyEntry(
                key = apiKey.trim(),
                lastError = e.message ?: "Network error",
                lastCheckedAtMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun synthesizeScene(
        rawTagalogNarration: String,
        voiceId: String,
        keyPool: List<ElevenLabsKeyEntry>,
        allowOfflineFallback: Boolean,
        tempCacheDir: File,
        onKeyStatusUpdate: suspend (ElevenLabsKeyEntry) -> Unit
    ): TtsSynthesisOutput = withContext(Dispatchers.IO) {
        val normalizedText = TagalogPhoneticNormalizer.normalize(rawTagalogNarration)
        val displayWords = rawTagalogNarration.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (keyPool.isEmpty()) {
            if (allowOfflineFallback) {
                return@withContext synthesizeOfflineFallback(
                    normalizedText = normalizedText,
                    displayWords = displayWords,
                    tempCacheDir = tempCacheDir
                )
            } else {
                throw IOException("No ElevenLabs API keys configured. Add a key in API & Settings.")
            }
        }

        val errors = mutableListOf<String>()
        for (entry in keyPool) {
            val quota = checkSubscriptionQuota(entry.key)
            onKeyStatusUpdate(quota)
            if (quota.lastError != null && (quota.lastError.contains("401") || quota.lastError.contains("403"))) {
                errors.add("${entry.maskedKey}: ${quota.lastError}")
                continue
            }
            if (quota.remainingChars != null && quota.remainingChars < normalizedText.length + 5) {
                val exhausted = quota.copy(lastError = "Quota exhausted (${quota.remainingChars} left)")
                onKeyStatusUpdate(exhausted)
                errors.add("${entry.maskedKey}: Quota exhausted")
                continue
            }

            try {
                val payload = JSONObject().apply {
                    put("text", normalizedText)
                    put("model_id", "eleven_multilingual_v2")
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.45)
                        put("similarity_boost", 0.78)
                        put("style", 0.35)
                    })
                }

                val req = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/${voiceId.trim()}/with-timestamps")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("xi-api-key", entry.key)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.code == 401 || resp.code == 429 || resp.code == 402) {
                        val updated = quota.copy(
                            lastError = "HTTP ${resp.code} rotated",
                            lastCheckedAtMs = System.currentTimeMillis()
                        )
                        onKeyStatusUpdate(updated)
                        errors.add("${entry.maskedKey}: HTTP ${resp.code}")
                        return@use
                    }
                    if (!resp.isSuccessful) {
                        throw IOException("ElevenLabs HTTP ${resp.code}: ${bodyStr.take(180)}")
                    }

                    val json = JSONObject(bodyStr)
                    val audioB64 = json.getString("audio_base64")
                    val mp3Bytes = Base64.decode(audioB64, Base64.DEFAULT)

                    val alignment = json.optJSONObject("alignment")
                        ?: json.optJSONObject("normalized_alignment")
                    val timings = mapAlignmentToDisplayWords(alignment, displayWords)

                    return@withContext TtsSynthesisOutput(
                        audioBytes = mp3Bytes,
                        isWav = false,
                        localWordTimings = timings,
                        keyUsed = entry.maskedKey
                    )
                }
            } catch (e: Exception) {
                errors.add("${entry.maskedKey}: ${e.message}")
            }
        }

        if (allowOfflineFallback) {
            return@withContext synthesizeOfflineFallback(
                normalizedText = normalizedText,
                displayWords = displayWords,
                tempCacheDir = tempCacheDir
            )
        }
        throw IOException("All ElevenLabs keys failed or exhausted: ${errors.joinToString(" | ")}")
    }

    private fun mapAlignmentToDisplayWords(
        alignment: JSONObject?,
        displayWords: List<String>
    ): List<WordTiming> {
        if (alignment == null || displayWords.isEmpty()) return emptyList()
        val chars = alignment.optJSONArray("characters") ?: return emptyList()
        val starts = alignment.optJSONArray("character_start_times_seconds") ?: return emptyList()
        val ends = alignment.optJSONArray("character_end_times_seconds") ?: return emptyList()
        if (chars.length() == 0) return emptyList()

        // Build aligned word spans from normalized character alignment
        val alignedWords = mutableListOf<Pair<Double, Double>>()
        var wordStart = -1.0
        var wordEnd = 0.0
        for (i in 0 until chars.length()) {
            val ch = chars.optString(i, " ")
            val s = starts.optDouble(i, 0.0) * 1000.0
            val e = ends.optDouble(i, s / 1000.0 + 0.05) * 1000.0
            if (ch.isBlank()) {
                if (wordStart >= 0.0) {
                    alignedWords.add(wordStart to wordEnd.coerceAtLeast(wordStart + 40.0))
                    wordStart = -1.0
                }
            } else {
                if (wordStart < 0.0) wordStart = s
                wordEnd = e
            }
        }
        if (wordStart >= 0.0) {
            alignedWords.add(wordStart to wordEnd.coerceAtLeast(wordStart + 40.0))
        }

        if (alignedWords.isEmpty()) return emptyList()
        val totalStart = alignedWords.first().first
        val totalEnd = alignedWords.last().second

        return if (alignedWords.size == displayWords.size) {
            displayWords.mapIndexed { idx, w ->
                WordTiming(w, alignedWords[idx].first, alignedWords[idx].second)
            }
        } else {
            // Proportional mapping onto displayWords preserving exact total span
            val span = (totalEnd - totalStart).coerceAtLeast(300.0)
            val totalChars = displayWords.sumOf { it.length.coerceAtLeast(1) }.toDouble()
            var cursor = totalStart
            displayWords.map { word ->
                val weight = word.length.coerceAtLeast(1) / totalChars
                val dur = span * weight
                val wt = WordTiming(word, cursor, cursor + dur)
                cursor += dur
                wt
            }
        }
    }

    private suspend fun synthesizeOfflineFallback(
        normalizedText: String,
        displayWords: List<String>,
        tempCacheDir: File
    ): TtsSynthesisOutput {
        val wavFile = File(tempCacheDir, "tts_offline_${UUID.randomUUID()}.wav")
        return try {
            val synthesizedOk = synthesizeWithAndroidTts(normalizedText, wavFile)
            if (synthesizedOk && wavFile.exists() && wavFile.length() > 44) {
                val wavBytes = wavFile.readBytes()
                TtsSynthesisOutput(
                    audioBytes = wavBytes,
                    isWav = true,
                    localWordTimings = buildEvenWordTimings(displayWords, estimateSpeechDurationMs(displayWords)),
                    keyUsed = "Offline Android TTS"
                )
            } else {
                // Silent paced PCM WAV fallback so rendering still works on headless devices without TTS voices
                val durMs = estimateSpeechDurationMs(displayWords)
                val silentWav = createSilentWav44100Mono(durMs)
                TtsSynthesisOutput(
                    audioBytes = silentWav,
                    isWav = true,
                    localWordTimings = buildEvenWordTimings(displayWords, durMs),
                    keyUsed = "Silent Paced Fallback"
                )
            }
        } finally {
            runCatching { if (wavFile.exists()) wavFile.delete() }
        }
    }

    private suspend fun synthesizeWithAndroidTts(text: String, outFile: File): Boolean =
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            var resumed = false
            fun finish(ok: Boolean) {
                if (!resumed) {
                    resumed = true
                    runCatching {
                        tts?.stop()
                        tts?.shutdown()
                    }
                    if (cont.isActive) cont.resume(ok)
                }
            }

            tts = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    finish(false)
                    return@TextToSpeech
                }
                val engine = tts ?: run { finish(false); return@TextToSpeech }
                val filLocale = Locale("fil", "PH")
                val res = engine.setLanguage(filLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
                engine.setSpeechRate(1.05f)
                val uttId = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == uttId) finish(true)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == uttId) finish(false)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == uttId) finish(false)
                    }
                })
                val params = Bundle()
                val synthResult = engine.synthesizeToFile(text, params, outFile, uttId)
                if (synthResult != TextToSpeech.SUCCESS) {
                    finish(false)
                }
            }

            cont.invokeOnCancellation {
                runCatching {
                    tts?.stop()
                    tts?.shutdown()
                }
            }
        }

    private fun estimateSpeechDurationMs(words: List<String>): Double {
        return (words.size * 380.0).coerceIn(3000.0, 6000.0)
    }

    private fun buildEvenWordTimings(words: List<String>, totalMs: Double): List<WordTiming> {
        if (words.isEmpty()) return emptyList()
        val totalChars = words.sumOf { it.length.coerceAtLeast(1) }.toDouble()
        var cursor = 120.0
        val usable = (totalMs - 240.0).coerceAtLeast(600.0)
        return words.map { w ->
            val dur = usable * (w.length.coerceAtLeast(1) / totalChars)
            val wt = WordTiming(w, cursor, cursor + dur)
            cursor += dur
            wt
        }
    }

    private fun createSilentWav44100Mono(durationMs: Double): ByteArray {
        val sampleRate = 44100
        val numSamples = ((durationMs / 1000.0) * sampleRate).toInt().coerceAtLeast(4410)
        val dataSize = numSamples * 2
        val totalSize = 44 + dataSize
        val out = ByteArray(totalSize)
        fun writeAscii(offset: Int, s: String) {
            s.forEachIndexed { idx, c -> out[offset + idx] = c.code.toByte() }
        }
        fun writeIntLE(offset: Int, v: Int) {
            out[offset] = (v and 0xFF).toByte()
            out[offset + 1] = ((v ushr 8) and 0xFF).toByte()
            out[offset + 2] = ((v ushr 16) and 0xFF).toByte()
            out[offset + 3] = ((v ushr 24) and 0xFF).toByte()
        }
        fun writeShortLE(offset: Int, v: Int) {
            out[offset] = (v and 0xFF).toByte()
            out[offset + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        writeAscii(0, "RIFF")
        writeIntLE(4, totalSize - 8)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1) // PCM
        writeShortLE(22, 1) // mono
        writeIntLE(24, sampleRate)
        writeIntLE(28, sampleRate * 2)
        writeShortLE(32, 2)
        writeShortLE(34, 16)
        writeAscii(36, "data")
        writeIntLE(40, dataSize)
        return out
    }
}
