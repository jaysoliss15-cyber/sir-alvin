package com.example.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import com.example.data.VideoGalleryRepository
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import com.example.model.ProductionMode
import com.example.model.TimelineScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

data class ExportResult(
    val mediaStoreUri: Uri,
    val displayName: String,
    val durationMs: Long
)

class DeterministicVideoExporter(
    private val galleryRepository: VideoGalleryRepository
) {
    companion object {
        const val VIDEO_WIDTH = 1080
        const val VIDEO_HEIGHT = 1920
        const val VIDEO_FPS = 30
        const val VIDEO_BITRATE = 8_000_000
        const val FRAME_DURATION_NS = 33_333_333L
        const val AUDIO_SAMPLE_RATE = 44100
        const val AUDIO_CHANNELS = 1
        const val AUDIO_BITRATE = 128_000
    }

    private class PendingPacket(
        val data: ByteArray,
        val info: MediaCodec.BufferInfo
    )

    suspend fun exportToMediaStore(
        scenes: List<TimelineScene>,
        unifiedPcm44100Mono: ByteArray,
        totalDurationMs: Double,
        style: AnimationStyle,
        displayName: String,
        tempCacheDir: File,
        onRenderProgress: (Float, String) -> Unit,
        onMuxProgress: (String) -> Unit,
        productionMode: ProductionMode = ProductionMode.FULL_AI_STORY,
        customSprites: Map<ActorRole, Bitmap> = emptyMap(),
        externalClipFrameSource: ExternalClipFrameSource? = null
    ): ExportResult = withContext(Dispatchers.Default) {
        val totalFrames = ceil((totalDurationMs / 1000.0) * VIDEO_FPS).toInt().coerceAtLeast(30)
        val tempMp4File = File(tempCacheDir, "render_${UUID.randomUUID()}.mp4")

        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var mediaMuxer: MediaMuxer? = null
        var inputSurface: Surface? = null
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        var frameBitmap: Bitmap? = null
        var glProgram = 0
        var glTextureId = 0

        try {
            // 1. Configure Video Encoder (video/avc, 1080x1920, 30fps, 8Mbps)
            val videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                VIDEO_WIDTH,
                VIDEO_HEIGHT
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            // 2. Configure Audio Encoder (audio/mp4a-latm, 44100Hz, mono, 128kbps)
            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            // 3. Setup MediaMuxer (do NOT start until BOTH encoders emit INFO_OUTPUT_FORMAT_CHANGED)
            mediaMuxer = MediaMuxer(tempMp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false
            val pendingVideo = mutableListOf<PendingPacket>()
            val pendingAudio = mutableListOf<PendingPacket>()

            fun maybeStartMuxer() {
                if (!muxerStarted && videoTrackIndex != -1 && audioTrackIndex != -1) {
                    mediaMuxer.start()
                    muxerStarted = true
                    for (pkt in pendingVideo) {
                        mediaMuxer.writeSampleData(videoTrackIndex, ByteBuffer.wrap(pkt.data), pkt.info)
                    }
                    pendingVideo.clear()
                    for (pkt in pendingAudio) {
                        mediaMuxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(pkt.data), pkt.info)
                    }
                    pendingAudio.clear()
                }
            }

            // 4. Prime Audio Encoder until it emits INFO_OUTPUT_FORMAT_CHANGED
            var pcmOffset = 0
            var audioSamplesFed = 0L
            var audioInputDone = false
            var audioOutputDone = false
            val audioBufferInfo = MediaCodec.BufferInfo()

            fun feedAudioStep(allowEos: Boolean) {
                if (audioInputDone) return
                if (!allowEos && pcmOffset >= unifiedPcm44100Mono.size) return
                val inIdx = audioEncoder.dequeueInputBuffer(2_000)
                if (inIdx >= 0) {
                    val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    if (pcmOffset >= unifiedPcm44100Mono.size) {
                        val ptsUs = (audioSamplesFed * 1_000_000L) / AUDIO_SAMPLE_RATE
                        audioEncoder.queueInputBuffer(
                            inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        audioInputDone = true
                    } else {
                        val toCopy = minOf(inBuf.remaining(), unifiedPcm44100Mono.size - pcmOffset)
                        inBuf.put(unifiedPcm44100Mono, pcmOffset, toCopy)
                        val ptsUs = (audioSamplesFed * 1_000_000L) / AUDIO_SAMPLE_RATE
                        pcmOffset += toCopy
                        audioSamplesFed += (toCopy / 2)
                        audioEncoder.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
                    }
                }
            }

            fun drainAudioStep(blockingTimeoutUs: Long) {
                while (!audioOutputDone) {
                    val outIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, blockingTimeoutUs)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            audioTrackIndex = mediaMuxer.addTrack(audioEncoder.outputFormat)
                            maybeStartMuxer()
                        }
                        outIdx >= 0 -> {
                            val outBuf = audioEncoder.getOutputBuffer(outIdx)
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                audioBufferInfo.size > 0 && outBuf != null
                            ) {
                                outBuf.position(audioBufferInfo.offset)
                                outBuf.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                if (muxerStarted) {
                                    mediaMuxer.writeSampleData(audioTrackIndex, outBuf, audioBufferInfo)
                                } else {
                                    val copy = ByteArray(audioBufferInfo.size)
                                    outBuf.get(copy)
                                    val infoCopy = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, audioBufferInfo.presentationTimeUs, audioBufferInfo.flags)
                                    }
                                    pendingAudio.add(PendingPacket(copy, infoCopy))
                                }
                            }
                            audioEncoder.releaseOutputBuffer(outIdx, false)
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                audioOutputDone = true
                            }
                        }
                    }
                }
            }

            // Prime AAC format before EGL loop
            var primeTries = 0
            while (audioTrackIndex == -1 && primeTries < 40) {
                if (pcmOffset < unifiedPcm44100Mono.size) {
                    feedAudioStep(allowEos = false)
                } else {
                    feedAudioStep(allowEos = true)
                }
                drainAudioStep(5_000)
                primeTries++
            }

            // 5. Initialize EGL14 Context & OpenGL ES 2.0 Textured Quad
            val eglSetup = setupEgl14(inputSurface)
            eglDisplay = eglSetup.display
            eglContext = eglSetup.context
            eglSurface = eglSetup.surface

            val glSetup = setupGlQuad()
            glProgram = glSetup.program
            glTextureId = glSetup.textureId

            frameBitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap)
            val fallbackBackdropCache = mutableMapOf<Int, Bitmap>()
            val videoBufferInfo = MediaCodec.BufferInfo()

            fun drainVideoStep(endOfStream: Boolean) {
                while (true) {
                    val outIdx = videoEncoder.dequeueOutputBuffer(
                        videoBufferInfo,
                        if (endOfStream) 10_000 else 0
                    )
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrackIndex = mediaMuxer.addTrack(videoEncoder.outputFormat)
                            maybeStartMuxer()
                        }
                        outIdx >= 0 -> {
                            val outBuf = videoEncoder.getOutputBuffer(outIdx)
                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                videoBufferInfo.size > 0 && outBuf != null
                            ) {
                                outBuf.position(videoBufferInfo.offset)
                                outBuf.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                if (muxerStarted) {
                                    mediaMuxer.writeSampleData(videoTrackIndex, outBuf, videoBufferInfo)
                                } else {
                                    val copy = ByteArray(videoBufferInfo.size)
                                    outBuf.get(copy)
                                    val infoCopy = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, videoBufferInfo.presentationTimeUs, videoBufferInfo.flags)
                                    }
                                    pendingVideo.add(PendingPacket(copy, infoCopy))
                                }
                            }
                            videoEncoder.releaseOutputBuffer(outIdx, false)
                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            // 6. Deterministic Frame-by-Frame EGL14 Render Loop
            for (frameIndex in 0 until totalFrames) {
                coroutineContext.ensureActive()

                val currentMs = frameIndex * (1000.0 / VIDEO_FPS.toDouble())
                frameBitmap.eraseColor(android.graphics.Color.BLACK)
                SceneCompositor.drawFrame(
                    canvas = frameCanvas,
                    width = VIDEO_WIDTH.toFloat(),
                    height = VIDEO_HEIGHT.toFloat(),
                    scenes = scenes,
                    style = style,
                    currentMs = currentMs,
                    fallbackBackdropCache = fallbackBackdropCache,
                    productionMode = productionMode,
                    customSprites = customSprites,
                    externalClipFrameProvider = externalClipFrameSource?.let { source ->
                        { sceneIdx, localMs -> source.getFrameBitmap(sceneIdx, localMs) }
                    }
                )

                GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glUseProgram(glProgram)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glTextureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frameBitmap, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Exact deterministic timestamp
                val ptsNs = frameIndex * FRAME_DURATION_NS
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                drainVideoStep(endOfStream = false)

                // Interleave audio feeding once muxer is running
                if (muxerStarted && !audioInputDone && pcmOffset < unifiedPcm44100Mono.size) {
                    feedAudioStep(allowEos = false)
                    drainAudioStep(0)
                }

                if (frameIndex % 15 == 0 || frameIndex == totalFrames - 1) {
                    val pct = (frameIndex + 1).toFloat() / totalFrames.toFloat()
                    onRenderProgress(pct, "Rendering frame ${frameIndex + 1}/$totalFrames (1080x1920 @ 30fps)")
                }
            }

            // Finish video stream
            onMuxProgress("Finalizing H.264 + AAC MP4 muxing...")
            videoEncoder.signalEndOfInputStream()
            drainVideoStep(endOfStream = true)

            // Finish remaining AAC audio stream
            while (!audioOutputDone) {
                if (!audioInputDone) {
                    feedAudioStep(allowEos = true)
                }
                drainAudioStep(10_000)
            }

            if (muxerStarted) {
                mediaMuxer.stop()
            } else {
                throw IllegalStateException("MediaMuxer failed to receive both video and audio output formats")
            }

            // 7. Save final .mp4 into Android MediaStore (Movies/ToonForgeTagalog)
            val savedUri = galleryRepository.saveMp4ToMediaStore(
                tempMp4File = tempMp4File,
                displayName = displayName,
                durationMs = totalDurationMs.toLong()
            )
            ExportResult(
                mediaStoreUri = savedUri,
                displayName = displayName,
                durationMs = totalDurationMs.toLong()
            )
        } finally {
            runCatching { frameBitmap?.recycle() }
            if (glTextureId != 0) {
                runCatching { GLES20.glDeleteTextures(1, intArrayOf(glTextureId), 0) }
            }
            if (glProgram != 0) {
                runCatching { GLES20.glDeleteProgram(glProgram) }
            }
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                runCatching {
                    EGL14.eglMakeCurrent(
                        eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                }
                if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                    runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
                }
                if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                    runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
                }
                runCatching { EGL14.eglTerminate(eglDisplay) }
            }
            runCatching { inputSurface?.release() }
            runCatching { videoEncoder?.stop() }
            runCatching { videoEncoder?.release() }
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            runCatching { mediaMuxer?.release() }
            runCatching { if (tempMp4File.exists()) tempMp4File.delete() }
        }
    }

    private data class EglHolder(
        val display: EGLDisplay,
        val context: EGLContext,
        val surface: EGLSurface
    )

    private data class GlHolder(
        val program: Int,
        val textureId: Int
    )

    private fun setupEgl14(surface: Surface): EglHolder {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
            throw RuntimeException("Unable to find RGB888+recordable EGL config")
        }

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context === EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL14 context")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], surface, surfaceAttribs, 0)
        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL window surface")
        }

        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            throw RuntimeException("eglMakeCurrent failed")
        }

        return EglHolder(display, context, eglSurface)
    }

    private fun setupGlQuad(): GlHolder {
        val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        GLES20.glUseProgram(program)

        // Fullscreen quad with upright Android Bitmap V-coordinate mapping
        val quadVertices = floatArrayOf(
            // X, Y, U, V
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )
        val vb: FloatBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        vb.position(0)

        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        val stride = 4 * 4

        vb.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, stride, vb)

        vb.position(2)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, stride, vb)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val samplerLoc = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glUniform1i(samplerLoc, 0)

        return GlHolder(program, texId)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }
}
