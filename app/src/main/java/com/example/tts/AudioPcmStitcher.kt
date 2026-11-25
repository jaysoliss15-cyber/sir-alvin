package com.example.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.model.AnimationStyle
import com.example.model.CameraMotion
import com.example.model.HudWidget
import com.example.model.SceneScript
import com.example.model.StatusPill
import com.example.model.WordTiming
import com.example.model.WorldFx
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

typealias PcmAudioStitcher = AudioPcmStitcher

data class DecodedSceneAudio(
    val pcm44100MonoBytes: ByteArray,
    val exactDurationMs: Double,
    val normalizedWordTimings: List<WordTiming>
)

data class StitchedAudioTimeline(
    val unifiedPcm44100Mono: ByteArray,
    val totalDurationMs: Double,
    val sceneStartMs: List<Double>,
    val sceneEndMs: List<Double>,
    val sceneWordTimings: List<List<WordTiming>>
)

object AudioPcmStitcher {

    const val TARGET_SAMPLE_RATE = 44100
    const val TARGET_CHANNELS = 1
    const val BGM_DUCKED_LEVEL = 0.10f
    const val BGM_PAUSE_LEVEL = 0.28f

    fun decodeAudioBytesToPcm44100Mono(
        audioBytes: ByteArray,
        isWav: Boolean,
        tempCacheDir: File
    ): ByteArray {
        val isWavHeader = isWav || (
            audioBytes.size > 12 &&
                audioBytes[0] == 'R'.code.toByte() &&
                audioBytes[1] == 'I'.code.toByte() &&
                audioBytes[2] == 'F'.code.toByte() &&
                audioBytes[3] == 'F'.code.toByte()
            )
        if (isWavHeader && audioBytes.size > 44) {
            return decodeWavBytesToPcm44100Mono(audioBytes)
        }

        val tempFile = File(tempCacheDir, "scene_audio_${UUID.randomUUID()}.mp3")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            tempFile.writeBytes(audioBytes)
            extractor.setDataSource(tempFile.absolutePath)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || inputFormat == null) {
                throw IllegalStateException("No audio track found in synthesized audio")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            var srcSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val rawPcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.get(chunk)
                            rawPcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            srcChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }

            return convertPcmTo44100Mono(
                pcmBytes = rawPcmOut.toByteArray(),
                srcSampleRate = srcSampleRate,
                srcChannels = srcChannels
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    private fun decodeWavBytesToPcm44100Mono(wavBytes: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = buf.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = buf.getInt(24).coerceAtLeast(8000)
        var offset = 12
        var dataOffset = 44
        var dataLen = (wavBytes.size - 44).coerceAtLeast(0)
        while (offset + 8 <= wavBytes.size) {
            val tag = String(wavBytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4)
            if (tag == "data") {
                dataOffset = offset + 8
                dataLen = chunkSize.coerceAtMost(wavBytes.size - dataOffset).coerceAtLeast(0)
                break
            }
            offset += 8 + chunkSize
        }
        val rawPcm = wavBytes.copyOfRange(dataOffset, dataOffset + dataLen)
        return convertPcmTo44100Mono(rawPcm, sampleRate, channels)
    }

    fun convertPcmTo44100Mono(
        pcmBytes: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int
    ): ByteArray {
        if (pcmBytes.isEmpty()) return ByteArray(0)
        val totalShorts = pcmBytes.size / 2
        val inBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val srcSamples = ShortArray(totalShorts)
        inBuf.get(srcSamples)

        val ch = srcChannels.coerceAtLeast(1)
        val monoFrames = totalShorts / ch
        val mono = ShortArray(monoFrames)
        if (ch == 1) {
            System.arraycopy(srcSamples, 0, mono, 0, monoFrames)
        } else {
            for (i in 0 until monoFrames) {
                var sum = 0
                for (c in 0 until ch) {
                    sum += srcSamples[i * ch + c].toInt()
                }
                mono[i] = (sum / ch).toShort()
            }
        }

        if (srcSampleRate == TARGET_SAMPLE_RATE) {
            val out = ByteArray(mono.size * 2)
            ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(mono)
            return out
        }

        val ratio = srcSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
        val dstCount = ((mono.size / ratio).toInt()).coerceAtLeast(1)
        val resampled = ShortArray(dstCount)
        for (i in 0 until dstCount) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, mono.size - 1)
            val idx1 = (idx0 + 1).coerceIn(0, mono.size - 1)
            val frac = srcPos - idx0
            val sample = mono[idx0] * (1.0 - frac) + mono[idx1] * frac
            resampled[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val out = ByteArray(resampled.size * 2)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampled)
        return out
    }

    fun stitchScenes(
        decodedPcmPerScene: List<ByteArray>,
        localTimingsPerScene: List<List<WordTiming>>,
        narrationPerScene: List<String>,
        scenes: List<SceneScript> = emptyList(),
        style: AnimationStyle = AnimationStyle.PAPER_DIORAMA,
        customBgmPcm44100Mono: ByteArray? = null
    ): StitchedAudioTimeline {
        val unifiedStream = ByteArrayOutputStream()
        val starts = mutableListOf<Double>()
        val ends = mutableListOf<Double>()
        val globalTimings = mutableListOf<List<WordTiming>>()

        var cumulativeOffsetMs = 0.0
        for (i in decodedPcmPerScene.indices) {
            val pcm = decodedPcmPerScene[i]
            val exactDurationMs = (pcm.size / 2.0 / TARGET_SAMPLE_RATE.toDouble()) * 1000.0
            unifiedStream.write(pcm)

            starts.add(cumulativeOffsetMs)
            ends.add(cumulativeOffsetMs + exactDurationMs)

            val rawTimings = localTimingsPerScene.getOrNull(i).orEmpty()
            val shiftedTimings = if (rawTimings.isNotEmpty()) {
                rawTimings.map { wt ->
                    WordTiming(
                        word = wt.word,
                        startMs = (cumulativeOffsetMs + wt.startMs).coerceIn(cumulativeOffsetMs, cumulativeOffsetMs + exactDurationMs),
                        endMs = (cumulativeOffsetMs + wt.endMs).coerceIn(cumulativeOffsetMs, cumulativeOffsetMs + exactDurationMs)
                    )
                }
            } else {
                val words = narrationPerScene.getOrNull(i).orEmpty()
                    .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isEmpty()) {
                    emptyList()
                } else {
                    val totalChars = words.sumOf { it.length.coerceAtLeast(1) }.toDouble()
                    var cursor = cumulativeOffsetMs + 50.0
                    val usable = (exactDurationMs - 100.0).coerceAtLeast(100.0)
                    words.map { w ->
                        val dur = usable * (w.length.coerceAtLeast(1) / totalChars)
                        val wt = WordTiming(w, cursor, cursor + dur)
                        cursor += dur
                        wt
                    }
                }
            }
            globalTimings.add(shiftedTimings)
            cumulativeOffsetMs += exactDurationMs
        }

        val dryVoicePcm = unifiedStream.toByteArray()
        val finalPcm = if (scenes.isNotEmpty() && dryVoicePcm.size >= 2) {
            mixSfxAndAutoDuckingBgm(
                dryVoicePcm = dryVoicePcm,
                scenes = scenes,
                sceneStartMs = starts,
                sceneEndMs = ends,
                style = style,
                customBgmPcm44100Mono = customBgmPcm44100Mono
            )
        } else {
            dryVoicePcm
        }

        return StitchedAudioTimeline(
            unifiedPcm44100Mono = finalPcm,
            totalDurationMs = cumulativeOffsetMs,
            sceneStartMs = starts,
            sceneEndMs = ends,
            sceneWordTimings = globalTimings
        )
    }

    /**
     * Synthesizes & mixes 44100Hz 16-bit procedural SFX (whoosh, pop, metallic ka-ching, sub-bass thud)
     * plus either an imported custom BGM loop or a warm lo-fi/synth pulse BGM loop with automatic
     * sidechain ducking (10% during active ElevenLabs speech, swelling to 28% during pauses).
     */
    fun mixSfxAndAutoDuckingBgm(
        dryVoicePcm: ByteArray,
        scenes: List<SceneScript>,
        sceneStartMs: List<Double>,
        sceneEndMs: List<Double>,
        style: AnimationStyle,
        customBgmPcm44100Mono: ByteArray? = null
    ): ByteArray {
        val sampleCount = dryVoicePcm.size / 2
        if (sampleCount == 0) return dryVoicePcm

        val voiceIn = ShortArray(sampleCount)
        ByteBuffer.wrap(dryVoicePcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(voiceIn)

        val mix = FloatArray(sampleCount)
        val speechEnvelope = FloatArray(sampleCount)

        // 1. Copy dry voice & extract smooth voice-activity envelope for sidechain ducking
        var env = 0f
        for (i in 0 until sampleCount) {
            val v = voiceIn[i] / 32768f
            mix[i] = v
            val level = abs(v)
            val coeff = if (level > env) 0.010f else 0.00016f
            env += (level - env) * coeff
            speechEnvelope[i] = (env * 5.8f).coerceIn(0f, 1f)
        }

        // 2. Render custom BGM (if imported) or warm ambient lo-fi / synth pulse loop with sidechain ducking
        if (customBgmPcm44100Mono != null && customBgmPcm44100Mono.size >= 4) {
            mixCustomBgmWithDucking(
                mix = mix,
                speechEnvelope = speechEnvelope,
                totalSamples = sampleCount,
                bgmPcmBytes = customBgmPcm44100Mono
            )
        } else {
            mixAmbientLoFiPulse(
                mix = mix,
                speechEnvelope = speechEnvelope,
                totalSamples = sampleCount
            )
        }

        // 3. Synthesize and mix frame-accurate procedural SFX for scene events
        for (idx in scenes.indices) {
            val scene = scenes[idx]
            val startMs = sceneStartMs.getOrNull(idx) ?: continue
            val endMs = sceneEndMs.getOrNull(idx) ?: startMs
            val durMs = (endMs - startMs).coerceAtLeast(300.0)
            val startSample = ((startMs / 1000.0) * TARGET_SAMPLE_RATE).toInt().coerceIn(0, sampleCount - 1)

            // Soft filtered noise whoosh on whip-pan transitions
            if (scene.cameraMotion == CameraMotion.WHIP_PAN_LEFT ||
                scene.cameraMotion == CameraMotion.WHIP_PAN_RIGHT ||
                (style == AnimationStyle.PAPER_DIORAMA && idx > 0)
            ) {
                addFilteredWhooshSfx(mix, startSample, seed = 409 + idx)
            }

            // Crisp pitch-envelope pop when status_pill or callout_banner appears
            val effectivePill = if (scene.statusPill != StatusPill.NONE) {
                scene.statusPill
            } else if (style == AnimationStyle.TECH_EXPLAINER || style == AnimationStyle.CROSS_SECTION) {
                when ((scene.sceneId - 1).coerceAtLeast(0) % 4) {
                    0 -> StatusPill.GOAL
                    1 -> StatusPill.PROBLEM
                    2 -> StatusPill.FIX
                    else -> StatusPill.SECRET
                }
            } else StatusPill.NONE

            val hasPill = effectivePill != StatusPill.NONE
            val hasCallout = scene.calloutBanner.isNotBlank() ||
                style == AnimationStyle.TECH_EXPLAINER ||
                style == AnimationStyle.CROSS_SECTION

            if (hasPill) {
                val pillSample = startSample + (0.045 * TARGET_SAMPLE_RATE).toInt()
                addPitchPopSfx(mix, pillSample, startFreqHz = 480.0, peakFreqHz = 1120.0, gain = 0.18f)
            }
            if (effectivePill == StatusPill.PROBLEM) {
                val thudSample = startSample + (0.060 * TARGET_SAMPLE_RATE).toInt()
                addSubBassThudSfx(mix, thudSample, gain = 0.30f)
            }
            if (hasCallout) {
                val calloutSample = startSample + (0.110 * TARGET_SAMPLE_RATE).toInt()
                addPitchPopSfx(mix, calloutSample, startFreqHz = 560.0, peakFreqHz = 1360.0, gain = 0.21f)
            }

            // High metallic double-note "ka-ching" chime during COIN_RAIN
            if (scene.worldFx == WorldFx.COIN_RAIN) {
                val offsetsMs = listOf(160.0, 960.0, 1820.0, 2680.0)
                offsetsMs.forEachIndexed { hitIdx, off ->
                    if (off < durMs - 240.0) {
                        val s = startSample + ((off / 1000.0) * TARGET_SAMPLE_RATE).toInt()
                        val base = 1567.98 * (1.0 + hitIdx * 0.04)
                        addMetallicChimeSfx(mix, s, baseFreqHz = base, gain = 0.14f)
                        addMetallicChimeSfx(
                            mix = mix,
                            startSample = s + (0.075 * TARGET_SAMPLE_RATE).toInt(),
                            baseFreqHz = base * 1.3348,
                            gain = 0.15f
                        )
                    }
                }
            }

            // Crisp ascending chime tick for each ANIMATED_CHECKLIST row reveal (every 0.6s)
            val effectiveWidget = if (scene.hudWidget != HudWidget.NONE) {
                scene.hudWidget
            } else if ((style == AnimationStyle.TECH_EXPLAINER || style == AnimationStyle.CROSS_SECTION) &&
                ((scene.sceneId - 1).coerceAtLeast(0) % 3 == 2)
            ) {
                HudWidget.ANIMATED_CHECKLIST
            } else {
                HudWidget.NONE
            }
            if (effectiveWidget == HudWidget.ANIMATED_CHECKLIST) {
                val itemCount = scene.checklistItems.size.coerceIn(1, 4).let {
                    if (scene.checklistItems.isEmpty()) 3 else it
                }
                for (itemIdx in 0 until itemCount) {
                    val tickMs = itemIdx * 600.0 + 140.0
                    if (tickMs < durMs - 120.0) {
                        val s = startSample + ((tickMs / 1000.0) * TARGET_SAMPLE_RATE).toInt()
                        addMetallicChimeSfx(
                            mix = mix,
                            startSample = s,
                            baseFreqHz = 1318.51 * (1.0 + itemIdx * 0.11),
                            gain = 0.13f
                        )
                    }
                }
            }
        }

        // 4. Soft-saturation limiter + 16-bit PCM pack
        val outBytes = ByteArray(sampleCount * 2)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until sampleCount) {
            val x = mix[i]
            val limited = if (abs(x) > 0.90f) {
                tanh(x.toDouble()).toFloat()
            } else {
                x
            }
            val pcmVal = (limited * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outBuf.put(i, pcmVal.toShort())
        }
        return outBytes
    }

    private fun mixCustomBgmWithDucking(
        mix: FloatArray,
        speechEnvelope: FloatArray,
        totalSamples: Int,
        bgmPcmBytes: ByteArray
    ) {
        val bgmLen = bgmPcmBytes.size / 2
        if (bgmLen <= 0) return
        val bgmShorts = ShortArray(bgmLen)
        ByteBuffer.wrap(bgmPcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(bgmShorts)

        val fadeSamples = (0.35 * TARGET_SAMPLE_RATE).toInt().coerceAtLeast(1)
        for (i in 0 until totalSamples) {
            val sample = bgmShorts[i % bgmLen] / 32768f
            val edgeFade = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > totalSamples - fadeSamples -> (totalSamples - i).coerceAtLeast(0).toFloat() / fadeSamples
                else -> 1f
            }
            val gain = (BGM_PAUSE_LEVEL - (BGM_PAUSE_LEVEL - BGM_DUCKED_LEVEL) * speechEnvelope[i]) * edgeFade
            mix[i] += sample * gain
        }
    }

    private fun mixAmbientLoFiPulse(
        mix: FloatArray,
        speechEnvelope: FloatArray,
        totalSamples: Int
    ) {
        // Warm 4-bar chill progression at 84 BPM: Fmaj7 -> Em7 -> Dm7 -> Cmaj7
        val chords = arrayOf(
            doubleArrayOf(174.61, 220.00, 261.63, 329.63),
            doubleArrayOf(164.81, 196.00, 246.94, 293.66),
            doubleArrayOf(146.83, 174.61, 220.00, 261.63),
            doubleArrayOf(130.81, 164.81, 196.00, 246.94)
        )
        val beatDurationSec = 60.0 / 84.0
        val barDurationSec = beatDurationSec * 4.0
        val sr = TARGET_SAMPLE_RATE.toDouble()

        val fadeSamples = (0.35 * sr).toInt().coerceAtLeast(1)
        for (i in 0 until totalSamples) {
            val t = i / sr
            val barFloat = (t / barDurationSec) % 4.0
            val chordIdx = barFloat.toInt().coerceIn(0, 3)
            val chord = chords[chordIdx]

            // Subtle sidechain pulse synchronized to beat
            val beatPhase = ((t / beatDurationSec) % 1.0)
            val pulseEnv = 0.72 + 0.28 * sin(beatPhase * PI)

            // Warm pad chord + gentle sub-bass root
            var pad = 0.0
            for (nIdx in chord.indices) {
                val freq = chord[nIdx]
                pad += sin(2.0 * PI * freq * t) * 0.20
                pad += sin(2.0 * PI * freq * 1.003 * t) * 0.06
            }
            val subBass = sin(2.0 * PI * (chord[0] * 0.5) * t) * 0.30 * pulseEnv
            val rawBgm = (pad * pulseEnv + subBass).toFloat()

            // Smooth fade-in/out on video edges
            val edgeFade = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > totalSamples - fadeSamples -> (totalSamples - i).coerceAtLeast(0).toFloat() / fadeSamples
                else -> 1f
            }

            // Sidechain ducking: 10% when speech is active, swells to 28% during pauses
            val gain = (BGM_PAUSE_LEVEL - (BGM_PAUSE_LEVEL - BGM_DUCKED_LEVEL) * speechEnvelope[i]) * edgeFade
            mix[i] += rawBgm * gain
        }
    }

    private fun addFilteredWhooshSfx(
        mix: FloatArray,
        startSample: Int,
        seed: Int
    ) {
        val len = (0.30 * TARGET_SAMPLE_RATE).toInt()
        val rng = Random(seed)
        var lp = 0f
        for (k in 0 until len) {
            val pos = startSample + k
            if (pos !in mix.indices) break
            val frac = k.toFloat() / len.toFloat()
            val env = sin(PI * frac).toFloat()
            val cutoff = 0.04f + 0.24f * env
            val white = rng.nextFloat() * 2f - 1f
            lp += (white - lp) * cutoff
            mix[pos] += lp * env * 0.24f
        }
    }

    private fun addPitchPopSfx(
        mix: FloatArray,
        startSample: Int,
        startFreqHz: Double,
        peakFreqHz: Double,
        gain: Float
    ) {
        val len = (0.09 * TARGET_SAMPLE_RATE).toInt()
        val attackSamples = (0.004 * TARGET_SAMPLE_RATE).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (k in 0 until len) {
            val pos = startSample + k
            if (pos !in mix.indices) break
            val t = k.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            val frac = k.toDouble() / len.toDouble()
            val freq = if (frac < 0.45) {
                startFreqHz + (peakFreqHz - startFreqHz) * (frac / 0.45)
            } else {
                peakFreqHz - (peakFreqHz - startFreqHz * 1.45) * ((frac - 0.45) / 0.55)
            }
            phase += (2.0 * PI * freq) / TARGET_SAMPLE_RATE.toDouble()
            val attack = if (k < attackSamples) k.toFloat() / attackSamples else 1f
            val decay = exp(-t * 34.0).toFloat()
            mix[pos] += sin(phase).toFloat() * attack * decay * gain
        }
    }

    private fun addMetallicChimeSfx(
        mix: FloatArray,
        startSample: Int,
        baseFreqHz: Double,
        gain: Float
    ) {
        val len = (0.26 * TARGET_SAMPLE_RATE).toInt()
        val attackSamples = (0.002 * TARGET_SAMPLE_RATE).toInt().coerceAtLeast(1)
        for (k in 0 until len) {
            val pos = startSample + k
            if (pos !in mix.indices) break
            val t = k.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            val attack = if (k < attackSamples) k.toFloat() / attackSamples else 1f
            val decay1 = exp(-t * 15.0)
            val decay2 = exp(-t * 24.0)
            val wave = (
                sin(2.0 * PI * baseFreqHz * t) * 0.55 * decay1 +
                    sin(2.0 * PI * (baseFreqHz * 1.503) * t) * 0.30 * decay1 +
                    sin(2.0 * PI * (baseFreqHz * 2.01) * t) * 0.22 * decay2
                ).toFloat()
            mix[pos] += wave * attack * gain
        }
    }

    private fun addSubBassThudSfx(
        mix: FloatArray,
        startSample: Int,
        gain: Float
    ) {
        val len = (0.24 * TARGET_SAMPLE_RATE).toInt()
        val attackSamples = (0.005 * TARGET_SAMPLE_RATE).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (k in 0 until len) {
            val pos = startSample + k
            if (pos !in mix.indices) break
            val t = k.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            val frac = k.toDouble() / len.toDouble()
            val freq = 115.0 * exp(-frac * 2.2) + 40.0
            phase += (2.0 * PI * freq) / TARGET_SAMPLE_RATE.toDouble()
            val attack = if (k < attackSamples) k.toFloat() / attackSamples else 1f
            val decay = exp(-t * 14.0).toFloat()
            mix[pos] += sin(phase).toFloat() * attack * decay * gain
        }
    }
}
