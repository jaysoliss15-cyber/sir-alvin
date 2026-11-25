package com.example.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import com.example.model.ActorRole
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object CharacterSpriteProcessor {

    private const val MAX_SPRITE_HEIGHT = 760

    fun importSpriteFromUri(
        context: Context,
        uri: Uri,
        role: ActorRole
    ): String {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("Could not decode PNG/image sprite")

        return try {
            val dir = File(context.filesDir, "custom_sprites").apply { mkdirs() }
            val outFile = File(dir, "${role.name}.png")
            val processed = prepareSpriteBitmap(raw)
            try {
                FileOutputStream(outFile).use { fos ->
                    processed.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
            } finally {
                if (processed !== raw && !processed.isRecycled) {
                    processed.recycle()
                }
            }
            outFile.absolutePath
        } finally {
            if (!raw.isRecycled) raw.recycle()
        }
    }

    fun prepareSpriteBitmap(source: Bitmap): Bitmap {
        val scaled = scaleToMaxHeight(source, MAX_SPRITE_HEIGHT)
        val eroded = erodeAlphaEdges(scaled, radiusPx = 1.5f)
        if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        val trimmed = trimTransparentBounds(eroded, paddingPx = 12)
        if (trimmed !== eroded && !eroded.isRecycled) eroded.recycle()
        return trimmed
    }

    fun loadCustomSpritesMap(
        paths: Map<String, String>,
        stickerOutline: Boolean
    ): Map<ActorRole, Bitmap> {
        if (paths.isEmpty()) return emptyMap()
        val result = mutableMapOf<ActorRole, Bitmap>()
        for ((roleKey, filePath) in paths) {
            val role = ActorRole.entries.firstOrNull { it.name == roleKey } ?: continue
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) continue
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: continue
            result[role] = if (stickerOutline) {
                val outlined = applyStickerOutline(decoded, outlinePx = 8)
                if (outlined !== decoded && !decoded.isRecycled) decoded.recycle()
                outlined
            } else {
                decoded
            }
        }
        return result
    }

    /**
     * Erodes semi-transparent PNG edges by 1.5px and strips bright white halo RGB fringing.
     */
    fun erodeAlphaEdges(source: Bitmap, radiusPx: Float = 1.5f): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = erodeAlphaBuffer(pixels, w, h, radiusPx)
        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Pure JVM-testable 1.5px morphological alpha erosion + halo suppression on ARGB_8888 pixel array.
     */
    fun erodeAlphaBuffer(
        src: IntArray,
        width: Int,
        height: Int,
        radiusPx: Float = 1.5f
    ): IntArray {
        val out = IntArray(src.size)
        val rCeil = ceil(radiusPx).toInt().coerceAtLeast(1)

        // Precompute neighbor offsets inside circular radius
        val offsets = mutableListOf<Triple<Int, Int, Float>>()
        for (dy in -rCeil..rCeil) {
            for (dx in -rCeil..rCeil) {
                if (dx == 0 && dy == 0) continue
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= radiusPx + 0.05f) {
                    // Weight: full strength inside 1.0px, smooth half-weight around 1.4..1.5px
                    val weight = ((radiusPx + 0.1f - dist) / 0.6f).coerceIn(0.35f, 1.0f)
                    offsets.add(Triple(dx, dy, weight))
                }
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val argb = src[idx]
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    out[idx] = 0
                    continue
                }

                var minNeighborAlpha = a
                for ((dx, dy, weight) in offsets) {
                    val nx = x + dx
                    val ny = y + dy
                    val nAlpha = if (nx in 0 until width && ny in 0 until height) {
                        (src[ny * width + nx] ushr 24) and 0xFF
                    } else {
                        0
                    }
                    val blendedMin = (nAlpha + (a - nAlpha) * (1f - weight)).roundToInt()
                    if (blendedMin < minNeighborAlpha) {
                        minNeighborAlpha = blendedMin
                    }
                }

                val finalA = minNeighborAlpha.coerceIn(0, 255)
                if (finalA < 8) {
                    out[idx] = 0
                    continue
                }

                var r = (argb ushr 16) and 0xFF
                var g = (argb ushr 8) and 0xFF
                var b = argb and 0xFF

                // Suppress bright white background fringe on eroded semi-transparent boundary pixels
                if (finalA < 220 && r > 205 && g > 205 && b > 205) {
                    val atten = 0.55f + 0.45f * (finalA / 220f)
                    r = (r * atten).roundToInt().coerceIn(0, 255)
                    g = (g * atten).roundToInt().coerceIn(0, 255)
                    b = (b * atten).roundToInt().coerceIn(0, 255)
                }

                out[idx] = (finalA shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    /**
     * Adds an 8px white sticker border (plus thin 2px charcoal outer edge) around the cutout silhouette.
     */
    fun applyStickerOutline(source: Bitmap, outlinePx: Int = 8): Bitmap {
        val w = source.width
        val h = source.height
        val pad = outlinePx + 4
        val newW = w + pad * 2
        val newH = h + pad * 2

        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val outPixels = IntArray(newW * newH)
        val whiteRadiusSq = outlinePx * outlinePx
        val darkRadiusSq = (outlinePx + 2) * (outlinePx + 2)

        // Stamp outer charcoal contrast edge, then inner 8px crisp white sticker border
        for (pass in 0..1) {
            val radius = if (pass == 0) outlinePx + 2 else outlinePx
            val radiusSq = if (pass == 0) darkRadiusSq else whiteRadiusSq
            val borderColor = if (pass == 0) 0xCC111827.toInt() else Color.WHITE

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val a = (srcPixels[y * w + x] ushr 24) and 0xFF
                    if (a < 110) continue
                    val cx = x + pad
                    val cy = y + pad
                    for (dy in -radius..radius) {
                        val ny = cy + dy
                        if (ny !in 0 until newH) continue
                        for (dx in -radius..radius) {
                            if (dx * dx + dy * dy > radiusSq) continue
                            val nx = cx + dx
                            if (nx !in 0 until newW) continue
                            val dstIdx = ny * newW + nx
                            if (pass == 0 || ((outPixels[dstIdx] ushr 24) and 0xFF) == 0 || pass == 1) {
                                outPixels[dstIdx] = borderColor
                            }
                        }
                    }
                }
            }
        }

        val outBmp = Bitmap.createBitmap(outPixels, newW, newH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        canvas.drawBitmap(source, pad.toFloat(), pad.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return outBmp
    }

    private fun scaleToMaxHeight(bmp: Bitmap, maxH: Int): Bitmap {
        if (bmp.height <= maxH) return bmp
        val ratio = maxH.toFloat() / bmp.height.toFloat()
        val newW = (bmp.width * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, newW, maxH, true)
    }

    private fun trimTransparentBounds(bmp: Bitmap, paddingPx: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = (pixels[y * w + x] ushr 24) and 0xFF
                if (a > 12) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return bmp

        val left = max(0, minX - paddingPx)
        val top = max(0, minY - paddingPx)
        val right = min(w, maxX + paddingPx + 1)
        val bottom = min(h, maxY + paddingPx + 1)
        return Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
    }

    fun deleteSpriteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val f = File(path)
            if (f.exists()) f.delete()
        }
    }
}
