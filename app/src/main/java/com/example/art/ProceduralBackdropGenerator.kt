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

        // Subtle graph-paper rules
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x148B95A5
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

        val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B2E3A.toInt()
            strokeWidth = (w * 0.0055f).coerceAtLeast(2f)
            setStyle(Paint.Style.STROKE)
            strokeCap = Paint.Cap.ROUND
        }
        val floorY = h * 0.72f
        canvas.drawLine(w * 0.08f, floorY, w * 0.92f, floorY, floorPaint)

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
        val u = w / 1080f
        val rng = Random(seed)

        // 1. Soft atmospheric sky gradient (golden dusk / starlight / warm studio sky / engineering cutaway)
        val skyColors = when (style) {
            AnimationStyle.DARK_SUSPENSE -> intArrayOf(0xFF0A0D18.toInt(), 0xFF1D1530.toInt(), 0xFF391D2C.toInt())
            AnimationStyle.CHIBI_STORYTIME -> intArrayOf(0xFF372C6B.toInt(), 0xFF7B5EA7.toInt(), 0xFFFFC1CC.toInt())
            AnimationStyle.TECH_EXPLAINER -> intArrayOf(0xFF0B1325.toInt(), 0xFF172C49.toInt(), 0xFF2B4D6F.toInt())
            AnimationStyle.CROSS_SECTION -> intArrayOf(0xFF0B1C36.toInt(), 0xFF15365C.toInt(), 0xFF285F82.toInt())
            AnimationStyle.VIRAL_2D_EXPLAINER -> intArrayOf(0xFF1E90FF.toInt(), 0xFF60C6FF.toInt(), 0xFFDFF6FF.toInt())
            AnimationStyle.FLAT_VECTOR -> when (biome) {
                SceneBiome.SPOOKY_NIGHT -> intArrayOf(0xFF101930.toInt(), 0xFF243860.toInt(), 0xFF475C88.toInt())
                SceneBiome.OFFICE_WEALTH -> intArrayOf(0xFF163A5F.toInt(), 0xFF2B6E8C.toInt(), 0xFFF3C68F.toInt())
                else -> intArrayOf(0xFF1D4E89.toInt(), 0xFF4BA3D8.toInt(), 0xFFFCE2A6.toInt())
            }
            else -> when (biome) {
                SceneBiome.SPOOKY_NIGHT -> intArrayOf(0xFF0D1428.toInt(), 0xFF202B4D.toInt(), 0xFF4A3D61.toInt())
                SceneBiome.OFFICE_WEALTH -> intArrayOf(0xFF1E3252.toInt(), 0xFF456990.toInt(), 0xFFF7C98A.toInt())
                else -> intArrayOf(0xFF225587.toInt(), 0xFF62A8DC.toInt(), 0xFFFFE1AB.toInt())
            }
        }

        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.68f,
                skyColors,
                floatArrayOf(0f, 0.54f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // 2. Warm Sun / Moon Glow with soft radial bloom
        val orbX = w * (0.26f + rng.nextFloat() * 0.48f)
        val orbY = h * 0.19f
        val orbRadius = w * 0.11f
        val isNight = biome == SceneBiome.SPOOKY_NIGHT || style == AnimationStyle.DARK_SUSPENSE
        val orbColor = when {
            style == AnimationStyle.DARK_SUSPENSE -> 0xFFF26A63.toInt()
            style == AnimationStyle.CHIBI_STORYTIME -> 0xFFFFECF2.toInt()
            style == AnimationStyle.TECH_EXPLAINER -> 0xFFFFE6A7.toInt()
            isNight -> 0xFFE8EEF5.toInt()
            else -> 0xFFFFE396.toInt()
        }
        val outerBloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                orbX, orbY, orbRadius * 4.0f,
                intArrayOf(
                    (orbColor and 0x00FFFFFF) or 0x6E000000,
                    (orbColor and 0x00FFFFFF) or 0x24000000,
                    0x00000000
                ),
                floatArrayOf(0.15f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(orbX, orbY, orbRadius * 4.0f, outerBloom)
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                orbX - orbRadius * 0.2f, orbY - orbRadius * 0.2f, orbRadius * 1.2f,
                intArrayOf(Color.WHITE, orbColor),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(orbX, orbY, orbRadius, orbPaint)

        // Subtle twilight stars on night/evening styles
        if (isNight || style == AnimationStyle.CHIBI_STORYTIME || style == AnimationStyle.TECH_EXPLAINER) {
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 32) {
                val sx = rng.nextFloat() * w
                val sy = rng.nextFloat() * h * 0.42f
                val sr = (1.2f + rng.nextFloat() * 2.5f) * u
                starPaint.color = Color.argb(110 + (i % 4) * 32, 255, 250, 235)
                canvas.drawCircle(sx, sy, sr, starPaint)
            }
        }

        // 3. Sculpted layered wispy diorama clouds (smooth organic curved paths, NO flat circles)
        val cloudColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0x44362445
            AnimationStyle.CHIBI_STORYTIME -> 0xCCFFF2F7.toInt()
            AnimationStyle.TECH_EXPLAINER -> 0x38A7C7E7
            else -> 0xD8FFF9EC.toInt()
        }
        val cloudShadow = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0x33120D1A
            AnimationStyle.TECH_EXPLAINER -> 0x26081222
            else -> 0x2A7C6F64
        }
        drawSculptedCloud(canvas, w * 0.22f, h * 0.145f, w * 0.34f, h * 0.042f, cloudColor, cloudShadow, u)
        drawSculptedCloud(canvas, w * 0.74f, h * 0.245f, w * 0.38f, h * 0.046f, cloudColor, cloudShadow, u)

        // 4. Layered rolling hills with atmospheric depth haze & tilt-shift blur
        val farHillColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFF1A1629.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF6950A8.toInt()
            AnimationStyle.TECH_EXPLAINER -> 0xFF203B56.toInt()
            else -> if (biome == SceneBiome.OFFICE_WEALTH) 0xFF334E70.toInt() else 0xFF38765B.toInt()
        }
        drawHillLayer(
            canvas = canvas, w = w, h = h,
            baseY = h * 0.545f, amp = h * 0.042f, freq = 1.45f, phase = rng.nextFloat() * 3.1f,
            topColor = farHillColor,
            bottomColor = darkenColor(farHillColor, 0.82f),
            paperShadow = true,
            blurRadius = 14f * u
        )

        // Atmospheric depth haze band between far and mid hills
        val horizonHazeColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0x335B2B3F
            AnimationStyle.TECH_EXPLAINER -> 0x308FB8DE
            else -> 0x38FFE7BA
        }
        val hazePaint = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.48f, 0f, h * 0.65f,
                intArrayOf(0x00000000, horizonHazeColor, 0x00000000),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.48f, w, h * 0.65f, hazePaint)

        val midHillColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFF131120.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF4F3889.toInt()
            AnimationStyle.TECH_EXPLAINER -> 0xFF162C42.toInt()
            else -> if (biome == SceneBiome.OFFICE_WEALTH) 0xFF263B57.toInt() else 0xFF2B6047.toInt()
        }
        drawHillLayer(
            canvas = canvas, w = w, h = h,
            baseY = h * 0.605f, amp = h * 0.032f, freq = 2.05f, phase = rng.nextFloat() * 4.2f + 1.2f,
            topColor = midHillColor,
            bottomColor = darkenColor(midHillColor, 0.78f),
            paperShadow = true,
            blurRadius = if (style == AnimationStyle.PAPER_DIORAMA) 6f * u else 0f
        )

        // 5. Detailed Midground Diorama Architecture & Trees (with lit windows, timber trim & cast shadows)
        drawMidgroundDioramaStructures(canvas, w, h, u, style, biome, rng)

        // 6. Foreground 2.5D Textured Stage (Cobblestone Pavers OR Layered Meadow Grass — zero wireframe grids)
        val groundTopY = h * 0.675f
        val groundColors = when (style) {
            AnimationStyle.DARK_SUSPENSE -> intArrayOf(0xFF1E2230.toInt(), 0xFF0C0E14.toInt())
            AnimationStyle.CHIBI_STORYTIME -> intArrayOf(0xFF59B886.toInt(), 0xFF2A6247.toInt())
            AnimationStyle.TECH_EXPLAINER -> intArrayOf(0xFF25374B.toInt(), 0xFF111A26.toInt())
            else -> when (biome) {
                SceneBiome.URBAN_CITY -> intArrayOf(0xFF474E63.toInt(), 0xFF232836.toInt())
                SceneBiome.OFFICE_WEALTH -> intArrayOf(0xFF594438.toInt(), 0xFF2D2019.toInt())
                else -> intArrayOf(0xFF56B47F.toInt(), 0xFF23533C.toInt())
            }
        }

        // Diorama shelf drop-shadow behind foreground stage lip
        val lipShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x5A000000
            maskFilter = BlurMaskFilter((20f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRect(0f, groundTopY - 10f * u, w, h, lipShadow)

        val groundPaint = Paint().apply {
            shader = LinearGradient(0f, groundTopY, 0f, h, groundColors[0], groundColors[1], Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, groundTopY, w, h, groundPaint)

        when {
            biome == SceneBiome.URBAN_CITY || biome == SceneBiome.OFFICE_WEALTH || biome == SceneBiome.SPOOKY_NIGHT -> {
                drawCobblestoneForeground(canvas, w, h, u, groundTopY, style, rng)
            }
            else -> {
                drawMeadowAndStonePathForeground(canvas, w, h, u, groundTopY, style, rng)
            }
        }

        // Soft warm rim highlight along stage horizon ledge
        val ledgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == AnimationStyle.DARK_SUSPENSE) 0x44F26A63 else 0x4DFFF8E7
            strokeWidth = (4.5f * u).coerceAtLeast(2f)
        }
        canvas.drawLine(0f, groundTopY, w, groundTopY, ledgePaint)

        // 7. Subtle Tilt-Shift Depth Haze & Warm Atmospheric Vignette
        val topBlurBand = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.16f,
                intArrayOf(0x48000000, 0x00000000),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h * 0.16f, topBlurBand)

        val bottomTiltShift = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.80f, 0f, h,
                intArrayOf(0x00000000, 0x8206080E.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.80f, w, h, bottomTiltShift)
    }

    private fun drawSculptedCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        width: Float,
        height: Float,
        fillColor: Int,
        shadowColor: Int,
        u: Float
    ) {
        fun cloudPath(ox: Float, oy: Float): Path {
            val l = cx - width * 0.5f + ox
            val r = cx + width * 0.5f + ox
            val base = cy + height * 0.45f + oy
            return Path().apply {
                moveTo(l + width * 0.08f, base)
                cubicTo(
                    l - width * 0.02f, base - height * 0.75f,
                    l + width * 0.22f, base - height * 1.15f,
                    l + width * 0.34f, base - height * 0.85f
                )
                cubicTo(
                    l + width * 0.42f, base - height * 1.55f,
                    l + width * 0.66f, base - height * 1.50f,
                    l + width * 0.72f, base - height * 0.82f
                )
                cubicTo(
                    l + width * 0.84f, base - height * 1.05f,
                    r + width * 0.02f, base - height * 0.60f,
                    r - width * 0.06f, base
                )
                close()
            }
        }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            maskFilter = BlurMaskFilter((8f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(cloudPath(4f * u, 9f * u), sp)
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        canvas.drawPath(cloudPath(0f, 0f), fp)
    }

    private fun drawHillLayer(
        canvas: Canvas,
        w: Float,
        h: Float,
        baseY: Float,
        amp: Float,
        freq: Float,
        phase: Float,
        topColor: Int,
        bottomColor: Int,
        paperShadow: Boolean,
        blurRadius: Float
    ) {
        val path = Path()
        path.moveTo(0f, h)
        val steps = 36
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = t * w
            val y = baseY - sin(t * PI.toFloat() * freq + phase) * amp -
                cos(t * PI.toFloat() * freq * 0.55f + 0.4f) * (amp * 0.45f)
            path.lineTo(x, y)
        }
        path.lineTo(w, h)
        path.close()

        if (paperShadow) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x3A000000
                maskFilter = BlurMaskFilter((w * 0.012f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.save()
            canvas.translate(0f, -h * 0.005f)
            canvas.drawPath(path, sp)
            canvas.restore()
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, baseY - amp * 1.5f, 0f, h * 0.72f, topColor, bottomColor, Shader.TileMode.CLAMP)
            if (blurRadius > 0f) {
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawPath(path, p)
    }

    private fun drawMidgroundDioramaStructures(
        canvas: Canvas,
        w: Float,
        h: Float,
        u: Float,
        style: AnimationStyle,
        biome: SceneBiome,
        rng: Random
    ) {
        val horizonY = h * 0.675f
        val wallColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFF191B29.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF583D84.toInt()
            AnimationStyle.TECH_EXPLAINER -> 0xFF1A2D42.toInt()
            else -> if (biome == SceneBiome.BARRIO_VILLAGE || biome == SceneBiome.NATURE_PATH) 0xFF6B4834.toInt() else 0xFF283952.toInt()
        }
        val sideWallColor = darkenColor(wallColor, 0.72f)
        val roofColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFF2B1C2E.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF9E4B79.toInt()
            else -> 0xFF8C432B.toInt()
        }
        val litWindowColor = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFFFF6B5B.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFFFFE680.toInt()
            AnimationStyle.TECH_EXPLAINER -> 0xFFFFD875.toInt()
            else -> 0xFFFFD573.toInt()
        }

        when (biome) {
            SceneBiome.URBAN_CITY, SceneBiome.OFFICE_WEALTH -> {
                val count = 6
                val slotW = w / count
                for (i in 0 until count) {
                    val left = i * slotW + slotW * 0.06f
                    val right = (i + 1) * slotW - slotW * 0.08f
                    val bHeight = h * (0.14f + rng.nextFloat() * 0.16f)
                    val top = horizonY - bHeight
                    val sideW = (right - left) * 0.16f

                    // Cast ground drop shadow behind building
                    val dropShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x3A000000
                        maskFilter = BlurMaskFilter((10f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawRect(left - 6f * u, top + 6f * u, right + sideW + 6f * u, horizonY, dropShadow)

                    // Front facade
                    canvas.drawRoundRect(
                        RectF(left, top, right, horizonY), 8f * u, 8f * u,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wallColor }
                    )
                    // 2.5D shaded right side wall
                    canvas.drawRect(
                        right - 2f * u, top + 6f * u, right + sideW, horizonY,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sideWallColor }
                    )
                    // Roof cornice ledge
                    canvas.drawRoundRect(
                        RectF(left - 4f * u, top - 8f * u, right + sideW + 2f * u, top + 6f * u),
                        4f * u, 4f * u,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lightenColor(wallColor, 1.18f) }
                    )

                    // Recessed glowing multi-pane windows
                    val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkenColor(wallColor, 0.55f) }
                    val panePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = litWindowColor }
                    var wy = top + 24f * u
                    while (wy < horizonY - 40f * u) {
                        var wx = left + (right - left) * 0.16f
                        while (wx < right - (right - left) * 0.24f) {
                            val ww = (right - left) * 0.18f
                            val wh = 26f * u
                            canvas.drawRoundRect(RectF(wx - 2f * u, wy - 2f * u, wx + ww + 2f * u, wy + wh + 2f * u), 3f * u, 3f * u, framePaint)
                            if (rng.nextFloat() > 0.30f) {
                                canvas.drawRoundRect(RectF(wx, wy, wx + ww, wy + wh), 2f * u, 2f * u, panePaint)
                            }
                            wx += (right - left) * 0.28f
                        }
                        wy += 48f * u
                    }
                }
            }
            SceneBiome.BARRIO_VILLAGE, SceneBiome.NATURE_PATH -> {
                drawLayeredTree(canvas, w * 0.09f, horizonY, h * 0.20f, u, style)
                drawLayeredTree(canvas, w * 0.91f, horizonY, h * 0.22f, u, style)
                drawDetailedWoodenHouse(
                    canvas = canvas, cx = w * 0.23f, groundY = horizonY, size = w * 0.29f, u = u,
                    wallColor = wallColor, sideColor = sideWallColor, roofColor = roofColor, windowColor = litWindowColor
                )
                drawDetailedWoodenHouse(
                    canvas = canvas, cx = w * 0.77f, groundY = horizonY, size = w * 0.25f, u = u,
                    wallColor = lightenColor(wallColor, 1.08f), sideColor = sideWallColor, roofColor = roofColor, windowColor = litWindowColor
                )
                drawLayeredTree(canvas, w * 0.43f, horizonY, h * 0.14f, u, style)
            }
            SceneBiome.SPOOKY_NIGHT -> {
                drawLayeredTree(canvas, w * 0.14f, horizonY, h * 0.23f, u, style)
                drawDetailedWoodenHouse(
                    canvas = canvas, cx = w * 0.76f, groundY = horizonY, size = w * 0.28f, u = u,
                    wallColor = wallColor, sideColor = sideWallColor, roofColor = roofColor, windowColor = litWindowColor
                )
                drawLayeredTree(canvas, w * 0.48f, horizonY, h * 0.17f, u, style)
                drawLayeredTree(canvas, w * 0.92f, horizonY, h * 0.21f, u, style)
            }
        }
    }

    private fun drawDetailedWoodenHouse(
        canvas: Canvas,
        cx: Float,
        groundY: Float,
        size: Float,
        u: Float,
        wallColor: Int,
        sideColor: Int,
        roofColor: Int,
        windowColor: Int
    ) {
        val houseW = size * 0.76f
        val bodyTop = groundY - size * 0.58f
        val stiltTop = groundY - size * 0.14f
        val left = cx - houseW * 0.5f
        val right = cx + houseW * 0.38f
        val sideRight = cx + houseW * 0.56f

        // Ground shadow under house
        val gShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x4D000000
            maskFilter = BlurMaskFilter((12f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(RectF(left - 18f * u, groundY - 12f * u, sideRight + 18f * u, groundY + 14f * u), gShadow)

        // Wooden stilts / stone foundation posts
        val postPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkenColor(wallColor, 0.55f) }
        for (px in listOf(left + 12f * u, cx - 6f * u, right - 12f * u, sideRight - 14f * u)) {
            canvas.drawRect(px, stiltTop - 4f * u, px + 11f * u, groundY, postPaint)
        }

        // Front timber wall + 2.5D shaded side wall
        canvas.drawRect(left, bodyTop, right, stiltTop, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wallColor })
        canvas.drawRect(right, bodyTop, sideRight, stiltTop, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sideColor })

        // Vertical timber plank seams
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x331B110B
            strokeWidth = (2.5f * u).coerceAtLeast(1f)
        }
        val planks = 6
        val pw = (right - left) / planks
        for (i in 1 until planks) {
            val sx = left + i * pw
            canvas.drawLine(sx, bodyTop, sx, stiltTop, seamPaint)
        }

        // Under-eaves cast shadow
        canvas.drawRect(left, bodyTop, sideRight, bodyTop + 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4D000000 })

        // Warm glowing cottage window with crossbars & sill
        val winW = houseW * 0.28f
        val winH = size * 0.20f
        val winL = left + houseW * 0.14f
        val winT = bodyTop + size * 0.12f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (windowColor and 0x00FFFFFF) or 0x55000000
            maskFilter = BlurMaskFilter((14f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(RectF(winL - 8f * u, winT - 8f * u, winL + winW + 8f * u, winT + winH + 8f * u), 8f * u, 8f * u, glow)
        canvas.drawRect(winL, winT, winL + winW, winT + winH, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = windowColor })
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkenColor(wallColor, 0.45f)
            setStyle(Paint.Style.STROKE)
            strokeWidth = (4f * u).coerceAtLeast(1.5f)
        }
        canvas.drawRect(winL, winT, winL + winW, winT + winH, framePaint)
        canvas.drawLine(winL + winW * 0.5f, winT, winL + winW * 0.5f, winT + winH, framePaint)
        canvas.drawLine(winL, winT + winH * 0.5f, winL + winW, winT + winH * 0.5f, framePaint)

        // Wooden door
        val doorL = right - houseW * 0.28f
        val doorR = right - houseW * 0.08f
        val doorT = bodyTop + size * 0.16f
        canvas.drawRect(doorL, doorT, doorR, stiltTop, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkenColor(wallColor, 0.58f) })
        canvas.drawCircle(doorR - 6f * u, (doorT + stiltTop) * 0.52f, 3f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD166.toInt() })

        // Pitched overlapping nipa/shingle roof
        val peakY = bodyTop - size * 0.42f
        val peakX = (left + right) * 0.5f
        val frontRoof = Path().apply {
            moveTo(peakX, peakY)
            lineTo(left - 18f * u, bodyTop + 10f * u)
            lineTo(right + 12f * u, bodyTop + 10f * u)
            close()
        }
        val sideRoof = Path().apply {
            moveTo(peakX, peakY)
            lineTo(right + 12f * u, bodyTop + 10f * u)
            lineTo(sideRight + 16f * u, bodyTop + 6f * u)
            lineTo(peakX + (sideRight - right), peakY + 4f * u)
            close()
        }
        canvas.drawPath(sideRoof, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkenColor(roofColor, 0.75f) })
        canvas.drawPath(frontRoof, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = roofColor })
    }

    private fun drawLayeredTree(
        canvas: Canvas,
        baseX: Float,
        groundY: Float,
        height: Float,
        u: Float,
        style: AnimationStyle
    ) {
        val trunkColor = if (style == AnimationStyle.DARK_SUSPENSE) 0xFF1D1821.toInt() else 0xFF4E342E.toInt()
        val leafDark = when (style) {
            AnimationStyle.DARK_SUSPENSE -> 0xFF131D29.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF3A7D6B.toInt()
            else -> 0xFF1E563B.toInt()
        }
        val leafLight = lightenColor(leafDark, 1.32f)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44000000 }
        canvas.drawOval(RectF(baseX - height * 0.25f, groundY - 8f * u, baseX + height * 0.25f, groundY + 10f * u), shadow)

        val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trunkColor }
        canvas.drawRoundRect(
            RectF(baseX - height * 0.045f, groundY - height * 0.55f, baseX + height * 0.045f, groundY),
            6f * u, 6f * u, trunkPaint
        )

        val crownCy = groundY - height * 0.65f
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = leafDark }
        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = leafLight }
        canvas.drawCircle(baseX - height * 0.14f, crownCy + height * 0.04f, height * 0.22f, darkPaint)
        canvas.drawCircle(baseX + height * 0.14f, crownCy + height * 0.05f, height * 0.20f, darkPaint)
        canvas.drawCircle(baseX, crownCy - height * 0.10f, height * 0.24f, darkPaint)
        canvas.drawCircle(baseX - height * 0.04f, crownCy - height * 0.14f, height * 0.17f, lightPaint)
    }

    private fun drawCobblestoneForeground(
        canvas: Canvas,
        w: Float,
        h: Float,
        u: Float,
        groundTopY: Float,
        style: AnimationStyle,
        rng: Random
    ) {
        val baseStone = if (style == AnimationStyle.DARK_SUSPENSE) 0xFF282D3E.toInt() else 0xFF565E73.toInt()
        val stonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val mortarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x550B0D14
            setStyle(Paint.Style.STROKE)
            strokeWidth = (2.5f * u).coerceAtLeast(1f)
        }

        val rows = 7
        val totalH = h - groundTopY
        for (r in 0 until rows) {
            val t0 = (r.toFloat() / rows)
            val t1 = ((r + 1).toFloat() / rows)
            val y0 = groundTopY + totalH * (t0 * t0 * 0.85f + t0 * 0.15f)
            val y1 = groundTopY + totalH * (t1 * t1 * 0.85f + t1 * 0.15f)
            val stoneH = (y1 - y0).coerceAtLeast(10f * u)
            val stoneW = (92f + r * 26f) * u
            val offsetX = if (r % 2 == 0) 0f else stoneW * 0.5f
            var sx = -stoneW + offsetX
            while (sx < w + stoneW) {
                val tone = 0.86f + rng.nextFloat() * 0.24f
                stonePaint.color = scaleColorBrightness(baseStone, tone)
                val rect = RectF(sx + 4f * u, y0 + 3f * u, sx + stoneW - 4f * u, y0 + stoneH - 3f * u)
                val radius = (stoneH * 0.28f).coerceAtLeast(4f * u)
                canvas.drawRoundRect(rect, radius, radius, stonePaint)
                canvas.drawRoundRect(rect, radius, radius, mortarPaint)
                sx += stoneW
            }
        }
    }

    private fun drawMeadowAndStonePathForeground(
        canvas: Canvas,
        w: Float,
        h: Float,
        u: Float,
        groundTopY: Float,
        style: AnimationStyle,
        rng: Random
    ) {
        // Curved warm stepping-stone garden path down the center
        val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == AnimationStyle.TECH_EXPLAINER) 0x44334A66 else 0x55D4B483
        }
        val path = Path().apply {
            moveTo(w * 0.43f, groundTopY)
            quadTo(w * 0.52f, (groundTopY + h) * 0.5f, w * 0.34f, h)
            lineTo(w * 0.68f, h)
            quadTo(w * 0.58f, (groundTopY + h) * 0.5f, w * 0.57f, groundTopY)
            close()
        }
        canvas.drawPath(path, pathPaint)

        // Stepping stones along path
        val stonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == AnimationStyle.TECH_EXPLAINER) 0xFF33475E.toInt() else 0xFFAD9B82.toInt()
        }
        for (i in 0 until 5) {
            val frac = (i + 0.4f) / 5.2f
            val sy = groundTopY + (h - groundTopY) * frac
            val sx = w * 0.50f + sin(frac * 3.1f) * 24f * u
            val rx = (44f + frac * 48f) * u
            val ry = (12f + frac * 14f) * u
            canvas.drawOval(RectF(sx - rx, sy - ry, sx + rx, sy + ry), stonePaint)
        }

        // Layered stylized grass tufts across meadow
        val tuftColor = when (style) {
            AnimationStyle.TECH_EXPLAINER -> 0xFF1C3847.toInt()
            AnimationStyle.CHIBI_STORYTIME -> 0xFF398862.toInt()
            else -> 0xFF2B6F4B.toInt()
        }
        val tuftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tuftColor
            strokeWidth = 4.5f * u
            strokeCap = Paint.Cap.ROUND
            setStyle(Paint.Style.STROKE)
        }
        for (i in 0 until 26) {
            val gx = rng.nextFloat() * w
            val gy = groundTopY + 18f * u + rng.nextFloat() * (h - groundTopY - 36f * u)
            if (gx in (w * 0.36f)..(w * 0.64f)) continue
            val gh = (14f + (gy - groundTopY) / (h - groundTopY) * 20f) * u
            canvas.drawLine(gx, gy, gx - 9f * u, gy - gh, tuftPaint)
            canvas.drawLine(gx, gy, gx, gy - gh * 1.15f, tuftPaint)
            canvas.drawLine(gx, gy, gx + 9f * u, gy - gh * 0.9f, tuftPaint)
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int = scaleColorBrightness(color, factor)
    private fun lightenColor(color: Int, factor: Float): Int = scaleColorBrightness(color, factor)

    private fun scaleColorBrightness(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
