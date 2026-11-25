package com.example.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.model.WordTiming
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

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

    fun decodeAudioBytesToPcm44100Mono(
        audioBytes: ByteArray,
        isWav: Boolean,
        tempCacheDir: File
    ): ByteArray {
        if (isWav && audioBytes.size > 44) {
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
        // Find "data" chunk
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

        // Downmix to mono
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

        // Linear interpolation resample to 44100Hz
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
        narrationPerScene: List<String>
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
                // Fallback proportional word timing over exact decoded PCM duration
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

        return StitchedAudioTimeline(
            unifiedPcm44100Mono = unifiedStream.toByteArray(),
            totalDurationMs = cumulativeOffsetMs,
            sceneStartMs = starts,
            sceneEndMs = ends,
            sceneWordTimings = globalTimings
        )
    }
}
