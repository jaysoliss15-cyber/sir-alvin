package com.example.art

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.AnimationStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object ProceduralBackdropGenerator {

    private enum class SceneBiome {
        URBAN_CITY,
        BARRIO_VILLAGE,
        OFFICE_WEALTH,
        SPOOKY_NIGHT,
        NATURE_PATH
    }

    fun generate(
        prompt: String,
        style: AnimationStyle,
        width: Int = 1080,
        height: Int = 1920,
        seed: Int = prompt.hashCode()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (style == AnimationStyle.STICKMAN_WHITEBOARD) {
            drawWhiteboardPaperCanvas(canvas, width, height, seed)
            return bitmap
        }

        val biome = classifyBiome(prompt, style)
        drawStylizedDiorama(canvas, width, height, style, biome, seed)
        return bitmap
    }

    private fun classifyBiome(prompt: String, style: AnimationStyle): SceneBiome {
        val p = prompt.lowercase()
        return when {
            style == AnimationStyle.DARK_SUSPENSE ||
                listOf("night", "dark", "horror", "ghost", "alley", "haunted", "shadow", "midnight", "grave")
                    .any { p.contains(it) } -> SceneBiome.SPOOKY_NIGHT
            listOf("office", "bank", "vault", "mansion", "gold", "rich", "desk", "money", "wallet")
                .any { p.contains(it) } -> SceneBiome.OFFICE_WEALTH
            listOf("city", "manila", "jeep", "street", "market", "store", "tindahan", "building")
                .any { p.contains(it) } -> SceneBiome.URBAN_CITY
            listOf("village", "barrio", "bahay", "house", "farm", "chicken", "province")
                .any { p.contains(it) } -> SceneBiome.BARRIO_VILLAGE
            else -> SceneBiome.NATURE_PATH
        }
    }

    private fun drawWhiteboardPaperCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        seed: Int
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rng = Random(seed)

        // Warm off-white textured paper base
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xFFFAF6EE.toInt(), 0xFFF3ECE0.toInt(), 0xFFEDE4D4.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Subtle whiteboard grid lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x168B95A5
            strokeWidth = (w * 0.002f).coerceAtLeast(1f)
        }
        val step = w * 0.08f
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }

        // Paper grain speckles
        val speckPaint = Paint().apply { isAntiAlias = false }
        for (i in 0 until 650) {
            speckPaint.color = if (i % 2 == 0) 0x12000000 else 0x18FFFFFF
            val sx = rng.nextFloat() * w
            val sy = rng.nextFloat() * h
            val r = (1f + rng.nextFloat() * 2.2f) * (w / 1080f)
            canvas.drawCircle(sx, sy, r, speckPaint)
        }

        // Subtle marker tray / floor horizon line
        val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B2E3A.toInt()
            strokeWidth = (w * 0.0055f).coerceAtLeast(2f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val floorY = h * 0.72f
        canvas.drawLine(w * 0.08f, floorY, w * 0.92f, floorY, floorPaint)

        // Soft paper edge vignette
        val vigPaint = Paint().apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.48f, h * 0.65f,
                intArrayOf(0x00000000, 0x229B8E78),
                floatArrayOf(0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, vigPaint)
    }

    private fun drawStylizedDiorama(
        canvas: Canvas,
        width: Int,
        height: Int,
        style: AnimationStyle,
        biome: SceneBiome,
        seed: Int
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rng = Random(seed)

        // 1. Atmospheric sky gradient
        val skyColors = when (style) {
            AnimationStyle.DARK_SUSPENSE -> intArrayOf(0xFF090B14.toInt(), 0xFF1A1026.toInt(), 0xFF2C1420.toInt())
            AnimationStyle.CHIBI_STORYTIME -> intArrayOf(0xFF2C2559.toInt(), 0xFF6B4E9B.toInt(), 0xFFFFAFBD.toInt())
            AnimationStyle.FLAT_VECTOR -> when (biome) {
                SceneBiome.SPOOKY_NIGHT -> intArrayOf(0xFF10182E.toInt(), 0xFF24345C.toInt(), 0xFF3E4E7A.toInt())
                SceneBiome.OFFICE_WEALTH -> intArrayOf(0xFF163A5F.toInt(), 0xFF256D85.toInt(), 0xFF8FE3CF.toInt())
                else -> intArrayOf(0xFF1D4E89.toInt(), 0xFF3FA7D6.toInt(), 0xFFFDE49E.toInt())
            }
            AnimationStyle.PAPER_DIORAMA -> when (biome) {
                SceneBiome.SPOOKY_NIGHT -> intArrayOf(0xFF0E1324.toInt(), 0xFF1E2749.toInt(), 0xFF3D3456.toInt())
                SceneBiome.OFFICE_WEALTH -> intArrayOf(0xFF1B2B44.toInt(), 0xFF3B5978.toInt(), 0xFFF4C07A.toInt())
                else -> intArrayOf(0xFF1D4C7A.toInt(), 0xFF569CD6.toInt(), 0xFFFFDCA4.toInt())
            }
            AnimationStyle.STICKMAN_WHITEBOARD -> intArrayOf(0xFFFAF6EE.toInt(), 0xFFF3ECE0.toInt(), 0xFFEDE4D4.toInt())
        }

        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.72f,
                skyColors,
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // 2. Celestial body (sun / moon / anime glow)
        val orbX = w * (0.28f + rng.nextFloat() * 0.44f)
        val orbY = h * 0.20f
        val orbRadius = w * 0.14f
        val orbColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFFE63946.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFFFFE5EC.toInt()
            else -> if (biome == SceneBiome.SPOOKY_NIGHT) 0xFFE2E8F0.toInt() else 0xFFFFE082.toInt()
        }
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                orbX, orbY, orbRadius * 2.5f,
                intArrayOf((orbColor and 0x00FFFFFF) or 0x66000000, 0x00000000),
                floatArrayOf(0.2f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(orbX, orbY, orbRadius * 2.5f, glowPaint)
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = orbColor }
        canvas.drawCircle(orbX, orbY, orbRadius, orbPaint)

        // Stars / clouds
        if (biome == SceneBiome.SPOOKY_NIGHT || style == AnimationStyle.DARK_SUSPENSE || style == AnimationStyle.CHIBI_STORYTIME) {
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt() }
            for (i in 0 until 28) {
                val sx = rng.nextFloat() * w
                val sy = rng.nextFloat() * h * 0.45f
                val sr = (1.5f + rng.nextFloat() * 3f) * (w / 1080f)
                canvas.drawCircle(sx, sy, sr, starPaint)
            }
        }

        // Stylized paper clouds
        val cloudColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0x332D1B36
            AnimationStyle.CHIBI_STORYTIME -> 0xBBFFF0F6.toInt()
            else -> 0xCCFFFDF7.toInt()
        }
        drawPaperCloud(canvas, w * 0.18f, h * 0.14f, w * 0.22f, cloudColor, style == AnimationStyle.PAPER_DIORAMA)
        drawPaperCloud(canvas, w * 0.76f, h * 0.24f, w * 0.25f, cloudColor, style == AnimationStyle.PAPER_DIORAMA)

        // 3. Distant parallax hill layer (tilt-shift blurred on Paper Diorama)
        val farHillColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFF151221.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF6247AA.toInt()
            else -> if (biome == SceneBiome.OFFICE_WEALTH) 0xFF2B4162.toInt() else 0xFF2D6A4F.toInt()
        }
        drawHillLayer(
            canvas, w, h,
            baseY = h * 0.57f,
            amp = h * 0.045f,
            freq = 1.6f,
            phase = rng.nextFloat() * 3f,
            color = farHillColor,
            paperShadow = style == AnimationStyle.PAPER_DIORAMA,
            blurRadius = if (style == AnimationStyle.PAPER_DIORAMA) w * 0.012f else 0f
        )

        // 4. Midground silhouettes (City / Barrio / Forest / Vault)
        drawMidgroundSilhouettes(canvas, w, h, style, biome, rng)

        // 5. Foreground 2.5D Isometric Stage / Ground Plane
        val groundTopY = h * 0.67f
        val groundColors = when (style) {
            AnimationStyle.DARK_SUSPENSE -> intArrayOf(0xFF191B26.toInt(), 0xFF0A0B10.toInt())
            AnimationStyle.CHIBI_STORYTIME -> intArrayOf(0xFF52B788.toInt(), 0xFF2D6A4F.toInt())
            else -> when (biome) {
                SceneBiome.URBAN_CITY -> intArrayOf(0xFF3A4154.toInt(), 0xFF202533.toInt())
                SceneBiome.OFFICE_WEALTH -> intArrayOf(0xFF4E3B31.toInt(), 0xFF2A1E18.toInt())
                else -> intArrayOf(0xFF52B788.toInt(), 0xFF1B4332.toInt())
            }
        }
        if (style == AnimationStyle.PAPER_DIORAMA) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55000000
                maskFilter = BlurMaskFilter((w * 0.018f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRect(0f, groundTopY - h * 0.01f, w, h, shadowPaint)
        }

        val groundPaint = Paint().apply {
            shader = LinearGradient(
                0f, groundTopY, 0f, h,
                groundColors[0], groundColors[1],
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, groundTopY, w, h, groundPaint)

        // Isometric perspective floor grid / path stripes
        val isoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            strokeWidth = (w * 0.003f).coerceAtLeast(1f)
        }
        val vanishX = w * 0.5f
        val vanishY = groundTopY - h * 0.08f
        for (i in -6..6) {
            val bottomX = vanishX + i * (w * 0.26f)
            canvas.drawLine(vanishX, vanishY, bottomX, h, isoPaint)
        }
        // Horizontal perspective lines
        for (row in 1..5) {
            val frac = (row / 5f)
            val yPos = groundTopY + (h - groundTopY) * (frac * frac)
            canvas.drawLine(0f, yPos, w, yPos, isoPaint)
        }

        // Paper-cut rim highlight on stage ledge
        val ledgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == AnimationStyle.DARK_SUSPENSE) 0x44EF4444 else 0x55FFFFFF
            strokeWidth = (w * 0.005f).coerceAtLeast(2f)
        }
        canvas.drawLine(0f, groundTopY, w, groundTopY, ledgePaint)

        // 6. Tilt-shift / soft vignette depth bands
        val topBlurBand = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.18f,
                intArrayOf(0x44000000, 0x00000000),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h * 0.18f, topBlurBand)

        val bottomDepthBand = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.78f, 0f, h,
                intArrayOf(0x00000000, 0x77000000),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.78f, w, h, bottomDepthBand)
    }

    private fun drawPaperCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        width: Float,
        color: Int,
        withShadow: Boolean
    ) {
        if (withShadow) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x2A000000 }
            canvas.drawCircle(cx - width * 0.2f + 8f, cy + 10f, width * 0.22f, sp)
            canvas.drawCircle(cx + width * 0.12f + 8f, cy - width * 0.05f + 10f, width * 0.27f, sp)
            canvas.drawCircle(cx + width * 0.35f + 8f, cy + 10f, width * 0.19f, sp)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(cx - width * 0.2f, cy, width * 0.22f, p)
        canvas.drawCircle(cx + width * 0.12f, cy - width * 0.05f, width * 0.27f, p)
        canvas.drawCircle(cx + width * 0.35f, cy, width * 0.19f, p)
    }

    private fun drawHillLayer(
        canvas: Canvas,
        w: Float,
        h: Float,
        baseY: Float,
        amp: Float,
        freq: Float,
        phase: Float,
        color: Int,
        paperShadow: Boolean,
        blurRadius: Float
    ) {
        val path = Path()
        path.moveTo(0f, h)
        val steps = 28
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = t * w
            val y = baseY - sin(t * PI.toFloat() * freq + phase) * amp -
                cos(t * PI.toFloat() * freq * 0.5f) * (amp * 0.45f)
            path.lineTo(x, y)
        }
        path.lineTo(w, h)
        path.close()

        if (paperShadow) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = 0x33000000
                maskFilter = BlurMaskFilter((w * 0.012f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.save()
            canvas.translate(0f, -h * 0.006f)
            canvas.drawPath(path, sp)
            canvas.restore()
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (blurRadius > 0f) {
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawPath(path, p)
    }

    private fun drawMidgroundSilhouettes(
        canvas: Canvas,
        w: Float,
        h: Float,
        style: AnimationStyle,
        biome: SceneBiome,
        rng: Random
    ) {
        val horizonY = h * 0.67f
        val silhouetteColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFF0D0F18.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF473170.toInt()
            else -> 0xFF213148.toInt()
        }
        val litWindowColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFFFF595E.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFFFFE66D.toInt()
            else -> 0xFFFFD166.toInt()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = silhouetteColor }
        val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = litWindowColor }

        when (biome) {
            SceneBiome.URBAN_CITY, SceneBiome.OFFICE_WEALTH -> {
                val count = 7
                val bw = w / count
                for (i in 0 until count) {
                    val left = i * bw + bw * 0.08f
                    val right = (i + 1) * bw - bw * 0.06f
                    val bHeight = h * (0.14f + rng.nextFloat() * 0.16f)
                    val top = horizonY - bHeight
                    canvas.drawRoundRect(RectF(left, top, right, horizonY), 10f, 10f, fillPaint)
                    // Lit windows
                    var wy = top + h * 0.025f
                    while (wy < horizonY - h * 0.03f) {
                        var wx = left + bw * 0.18f
                        while (wx < right - bw * 0.24f) {
                            if (rng.nextFloat() > 0.35f) {
                                canvas.drawRect(wx, wy, wx + bw * 0.16f, wy + h * 0.015f, winPaint)
                            }
                            wx += bw * 0.28f
                        }
                        wy += h * 0.032f
                    }
                }
            }
            SceneBiome.BARRIO_VILLAGE -> {
                // Stylized Bahay Kubo huts & coconut trees
                drawBahayKubo(canvas, w * 0.22f, horizonY, w * 0.28f, silhouetteColor, litWindowColor)
                drawBahayKubo(canvas, w * 0.78f, horizonY, w * 0.24f, silhouetteColor, litWindowColor)
                drawPalmTree(canvas, w * 0.12f, horizonY, h * 0.22f, silhouetteColor)
                drawPalmTree(canvas, w * 0.88f, horizonY, h * 0.20f, silhouetteColor)
            }
            SceneBiome.SPOOKY_NIGHT, SceneBiome.NATURE_PATH -> {
                // Layered stylized pines / acacia silhouettes
                for (i in 0 until 6) {
                    val tx = w * (0.08f + i * 0.17f)
                    val th = h * (0.14f + (i % 3) * 0.05f)
                    val treePath = Path().apply {
                        moveTo(tx, horizonY - th)
                        lineTo(tx - w * 0.11f, horizonY)
                        lineTo(tx + w * 0.11f, horizonY)
                        close()
                    }
                    canvas.drawPath(treePath, fillPaint)
                }
            }
        }
    }

    private fun drawBahayKubo(
        canvas: Canvas,
        cx: Float,
        groundY: Float,
        size: Float,
        bodyColor: Int,
        windowColor: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bodyColor }
        // Stilts
        canvas.drawRect(cx - size * 0.35f, groundY - size * 0.22f, cx - size * 0.28f, groundY, p)
        canvas.drawRect(cx + size * 0.28f, groundY - size * 0.22f, cx + size * 0.35f, groundY, p)
        // House body
        canvas.drawRect(cx - size * 0.38f, groundY - size * 0.62f, cx + size * 0.38f, groundY - size * 0.20f, p)
        // Steep nipa roof
        val roof = Path().apply {
            moveTo(cx, groundY - size * 1.05f)
            lineTo(cx - size * 0.52f, groundY - size * 0.58f)
            lineTo(cx + size * 0.52f, groundY - size * 0.58f)
            close()
        }
        canvas.drawPath(roof, p)
        // Warm window
        val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = windowColor }
        canvas.drawRect(cx - size * 0.15f, groundY - size * 0.48f, cx + size * 0.15f, groundY - size * 0.30f, wp)
    }

    private fun drawPalmTree(
        canvas: Canvas,
        baseX: Float,
        groundY: Float,
        height: Float,
        color: Int
    ) {
        val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = height * 0.055f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val topX = baseX + height * 0.10f
        val topY = groundY - height
        val trunk = Path().apply {
            moveTo(baseX, groundY)
            quadTo(baseX + height * 0.14f, groundY - height * 0.5f, topX, topY)
        }
        canvas.drawPath(trunk, trunkPaint)

        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        for (a in listOf(-150, -110, -45, 10, 45)) {
            val rad = Math.toRadians(a.toDouble())
            val lx = topX + cos(rad).toFloat() * height * 0.42f
            val ly = topY + sin(rad).toFloat() * height * 0.28f + height * 0.08f
            val frond = Path().apply {
                moveTo(topX, topY)
                quadTo((topX + lx) * 0.5f, topY - height * 0.12f, lx, ly)
                quadTo((topX + lx) * 0.5f, topY + height * 0.03f, topX, topY)
                close()
            }
            canvas.drawPath(frond, leafPaint)
        }
    }
}
