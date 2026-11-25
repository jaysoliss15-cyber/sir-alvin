package com.example.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes imported .mp4 video clips (via MediaExtractor + MediaCodec) and .jpg/.png images
 * sequentially on the fly, center-cropped to 1080x1920 with seamless looping when clips are
 * shorter than their assigned scene duration.
 */
class ExternalClipFrameSource(
    private val context: Context,
    private val clipUris: List<Uri>,
    private val targetWidth: Int = 1080,
    private val targetHeight: Int = 1920
) : Closeable {

    private class VideoClipDecoder(
        val uri: Uri,
        val extractor: MediaExtractor,
        val codec: MediaCodec,
        val trackIndex: Int,
        val durationUs: Long,
        val rotationDeg: Int,
        var lastDecodedBitmap: Bitmap? = null,
        var lastDecodedPtsUs: Long = -1L,
        var argbBuffer: IntArray? = null,
        var rawDecodeBitmap: Bitmap? = null
    ) {
        var sawInputEos = false
        var sawOutputEos = false
    }

    private val imageCache = mutableMapOf<Int, Bitmap>()
    private val videoDecoders = mutableMapOf<Int, VideoClipDecoder>()
    private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun hasClips(): Boolean = clipUris.isNotEmpty()

    fun getFrameBitmap(sceneIndex: Int, localTimeMs: Double): Bitmap? {
        if (clipUris.isEmpty()) return null
        val clipIndex = sceneIndex.coerceAtLeast(0) % clipUris.size
        val uri = clipUris[clipIndex]

        if (!isVideoUri(uri)) {
            return imageCache.getOrPut(clipIndex) {
                decodeScaledCenterCropImage(uri) ?: return null
            }
        }

        val decoder = videoDecoders.getOrPut(clipIndex) {
            createVideoDecoder(uri) ?: return imageCache.getOrPut(clipIndex) {
                decodeScaledCenterCropImage(uri) ?: return null
            }
        }

        return decodeNextVideoFrame(decoder, localTimeMs)
    }

    private fun isVideoUri(uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.startsWith("video/")) return true
        if (mime.startsWith("image/")) return false
        val path = uri.toString().lowercase()
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".mkv") || path.endsWith(".webm")
    }

    private fun decodeScaledCenterCropImage(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > targetWidth * 2 || bounds.outHeight / sampleSize > targetHeight * 2) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return null
            val cropped = scaleCenterCrop(raw, targetWidth, targetHeight, rotationDeg = 0)
            if (cropped !== raw && !raw.isRecycled) raw.recycle()
            cropped
        } catch (_: Throwable) {
            null
        }
    }

    private fun createVideoDecoder(uri: Uri): VideoClipDecoder? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    trackIdx = i
                    format = fmt
                    break
                }
            }
            if (trackIdx == -1 || format == null) {
                extractor.release()
                return null
            }
            extractor.selectTrack(trackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(33_333L)
            } else {
                10_000_000L
            }
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                format.getInteger(MediaFormat.KEY_ROTATION)
            } else 0

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            VideoClipDecoder(
                uri = uri,
                extractor = extractor,
                codec = codec,
                trackIndex = trackIdx,
                durationUs = durationUs,
                rotationDeg = rotation
            )
        } catch (_: Throwable) {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            null
        }
    }

    private fun decodeNextVideoFrame(dec: VideoClipDecoder, localTimeMs: Double): Bitmap? {
        val durUs = dec.durationUs.coerceAtLeast(33_333L)
        val rawTargetUs = (localTimeMs.coerceAtLeast(0.0) * 1000.0).toLong()
        val targetUs = rawTargetUs % durUs

        // Loop smoothly back to 0 when clip finishes before scene duration
        if (dec.lastDecodedPtsUs >= 0L && (targetUs + 120_000L < dec.lastDecodedPtsUs || dec.sawOutputEos && targetUs < dec.lastDecodedPtsUs)) {
            runCatching {
                dec.extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                dec.codec.flush()
                dec.sawInputEos = false
                dec.sawOutputEos = false
                dec.lastDecodedPtsUs = -1L
            }
        }

        if (dec.lastDecodedBitmap != null && dec.lastDecodedPtsUs >= targetUs) {
            return dec.lastDecodedBitmap
        }

        val info = MediaCodec.BufferInfo()
        var iterations = 0
        while (!dec.sawOutputEos && iterations < 120) {
            iterations++
            if (!dec.sawInputEos) {
                val inIdx = dec.codec.dequeueInputBuffer(4_000)
                if (inIdx >= 0) {
                    val inBuf = dec.codec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val sampleSize = dec.extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            dec.codec.queueInputBuffer(
                                inIdx, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            dec.sawInputEos = true
                        } else {
                            val pts = dec.extractor.sampleTime
                            dec.codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            dec.extractor.advance()
                        }
                    }
                }
            }

            val outIdx = dec.codec.dequeueOutputBuffer(info, 4_000)
            if (outIdx >= 0) {
                val pts = info.presentationTimeUs
                val shouldConvert = info.size > 0 && (dec.lastDecodedBitmap == null || pts >= targetUs - 20_000L)
                if (shouldConvert) {
                    val image = runCatching { dec.codec.getOutputImage(outIdx) }.getOrNull()
                    if (image != null) {
                        try {
                            updateDecoderBitmapFromImage(dec, image)
                            dec.lastDecodedPtsUs = pts
                        } finally {
                            image.close()
                        }
                    }
                }
                dec.codec.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    dec.sawOutputEos = true
                }
                if (dec.lastDecodedBitmap != null && pts >= targetUs) {
                    break
                }
            }
        }
        return dec.lastDecodedBitmap
    }

    private fun updateDecoderBitmapFromImage(dec: VideoClipDecoder, image: Image) {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixStride = uPlane.pixelStride

        val pixelCount = w * h
        val argb = dec.argbBuffer?.takeIf { it.size == pixelCount } ?: IntArray(pixelCount).also {
            dec.argbBuffer = it
        }

        for (y in 0 until h) {
            val yRowBase = y * yRowStride
            val uvRowBase = (y ushr 1) * uvRowStride
            for (x in 0 until w) {
                val yVal = (yBuf.get(yRowBase + x * yPixStride).toInt() and 0xFF)
                val uvOffset = uvRowBase + (x ushr 1) * uvPixStride
                val uVal = (uBuf.get(uvOffset).toInt() and 0xFF) - 128
                val vVal = (vBuf.get(uvOffset).toInt() and 0xFF) - 128

                val y1192 = 1192 * max(0, yVal - 16)
                var r = (y1192 + 1634 * vVal) shr 10
                var g = (y1192 - 833 * vVal - 400 * uVal) shr 10
                var b = (y1192 + 2066 * uVal) shr 10

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                argb[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val rawBmp = dec.rawDecodeBitmap?.takeIf { it.width == w && it.height == h && !it.isRecycled }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                dec.rawDecodeBitmap?.recycle()
                dec.rawDecodeBitmap = it
            }
        rawBmp.setPixels(argb, 0, w, 0, 0, w, h)

        val dstBmp = dec.lastDecodedBitmap?.takeIf { !it.isRecycled }
            ?: Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                dec.lastDecodedBitmap = it
            }
        drawScaledCenterCropInto(rawBmp, dstBmp, dec.rotationDeg)
    }

    private fun scaleCenterCrop(src: Bitmap, dstW: Int, dstH: Int, rotationDeg: Int): Bitmap {
        val dst = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        drawScaledCenterCropInto(src, dst, rotationDeg)
        return dst
    }

    private fun drawScaledCenterCropInto(src: Bitmap, dst: Bitmap, rotationDeg: Int) {
        val canvas = Canvas(dst)
        val effW = if (rotationDeg == 90 || rotationDeg == 270) src.height else src.width
        val effH = if (rotationDeg == 90 || rotationDeg == 270) src.width else src.height
        val scale = max(dst.width.toFloat() / effW.toFloat(), dst.height.toFloat() / effH.toFloat())
        val m = Matrix().apply {
            postTranslate(-src.width * 0.5f, -src.height * 0.5f)
            if (rotationDeg != 0) postRotate(rotationDeg.toFloat())
            postScale(scale, scale)
            postTranslate(dst.width * 0.5f, dst.height * 0.5f)
        }
        canvas.drawBitmap(src, m, cropPaint)
    }

    override fun close() {
        imageCache.values.forEach { bmp ->
            runCatching { if (!bmp.isRecycled) bmp.recycle() }
        }
        imageCache.clear()
        videoDecoders.values.forEach { dec ->
            runCatching { dec.codec.stop() }
            runCatching { dec.codec.release() }
            runCatching { dec.extractor.release() }
            runCatching { dec.rawDecodeBitmap?.recycle() }
            runCatching { dec.lastDecodedBitmap?.recycle() }
        }
        videoDecoders.clear()
    }
}
