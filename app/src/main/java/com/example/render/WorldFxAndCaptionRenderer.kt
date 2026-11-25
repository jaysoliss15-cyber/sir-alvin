package com.example.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.AnimationStyle
import com.example.model.WordTiming
import com.example.model.WorldFx
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object WorldFxAndCaptionRenderer {

    fun drawWorldFx(
        canvas: Canvas,
        w: Float,
        h: Float,
        fx: WorldFx,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val u = w / 1080f
        when (fx) {
            WorldFx.NONE -> Unit
            WorldFx.COIN_RAIN -> drawCoinRain(canvas, w, h, u, globalTimeSec)
            WorldFx.FIRE_SPARKS -> drawFireSparks(canvas, w, h, u, globalTimeSec)
            WorldFx.GIANT_HAND_GRAB -> drawGiantHandGrab(canvas, w, h, u, sceneElapsedSec)
            WorldFx.FLOATING_HOUSES -> drawFloatingHouses(canvas, w, h, u, sceneElapsedSec, globalTimeSec)
            WorldFx.CHALKBOARD_DRAW -> drawChalkboardDiagram(canvas, w, h, u, style, sceneElapsedSec)
            WorldFx.EGG_CRACK -> drawEggCrack(canvas, w, h, u, sceneElapsedSec)
        }
    }

    fun drawStyleOverlay(
        canvas: Canvas,
        w: Float,
        h: Float,
        style: AnimationStyle,
        globalTimeSec: Float
    ) {
        val u = w / 1080f
        when (style) {
            AnimationStyle.DARK_SUSPENSE -> {
                // Procedural dark vignette
                val vigPaint = Paint().apply {
                    shader = RadialGradient(
                        w * 0.5f, h * 0.45f, h * 0.62f,
                        intArrayOf(0x00000000, 0xCC050509.toInt()),
                        floatArrayOf(0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, vigPaint)

                // Animated film grain overlay
                val rng = Random((globalTimeSec * 24f).toInt())
                val grainPaint = Paint().apply { color = 0x1EFFFFFF }
                for (i in 0 until 140) {
                    val gx = rng.nextFloat() * w
                    val gy = rng.nextFloat() * h
                    canvas.drawRect(gx, gy, gx + 3f * u, gy + 3f * u, grainPaint)
                }
            }
            AnimationStyle.FLAT_VECTOR, AnimationStyle.TECH_EXPLAINER, AnimationStyle.CROSS_SECTION -> {
                // Floating subtle infographic accents (no cluttered grid lines)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (style) {
                        AnimationStyle.TECH_EXPLAINER -> 0x4400E5FF
                        AnimationStyle.CROSS_SECTION -> 0x4822D3EE
                        else -> 0x332DD4BF
                    }
                    setStyle(Paint.Style.STROKE)
                    strokeWidth = 5f * u
                }
                val yFloat = sin(globalTimeSec * 2.5f) * 16f * u
                canvas.drawCircle(w * 0.14f, h * 0.25f + yFloat, 28f * u, p)
                canvas.drawRoundRect(
                    RectF(w * 0.80f, h * 0.22f - yFloat, w * 0.88f, h * 0.26f - yFloat),
                    10f * u, 10f * u, p
                )
            }
            else -> Unit
        }
    }

    private val HIGHLIGHT_KEYWORDS = setOf(
        "SIKRETO", "LIBRE", "MALI", "TAMA", "IPON", "YAMAN", "BAKIT",
        "DELIKADO", "LAKAS", "PERA", "GRABE", "HINDI", "SAHOD", "UTANG",
        "NEGOSYO", "DISKARTE", "GOAL", "SOLUSYON"
    )

    private fun isEmphasizedWord(word: String): Boolean {
        val clean = word.trim().replace(Regex("[^\\p{L}\\p{N}₱%]"), "")
        if (clean.isEmpty()) return false
        if (word.contains("₱") || word.contains("%") || clean.any { it.isDigit() }) return true
        if (clean.uppercase() in HIGHLIGHT_KEYWORDS) return true
        return clean.length >= 3 && clean == clean.uppercase() && clean.any { it.isLetter() }
    }

    fun drawKaraokeCaptions(
        canvas: Canvas,
        w: Float,
        h: Float,
        style: AnimationStyle,
        wordTimings: List<WordTiming>,
        fallbackNarration: String,
        currentMs: Double
    ) {
        val u = w / 1080f
        val words = if (wordTimings.isNotEmpty()) {
            wordTimings
        } else {
            val raw = fallbackNarration.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            raw.mapIndexed { idx, s -> WordTiming(s, idx * 350.0, (idx + 1) * 350.0) }
        }
        if (words.isEmpty()) return

        // Group words into crisp 3-word karaoke chunks for viral 9:16 readability
        val activeWordIdx = words.indexOfFirst { currentMs >= it.startMs && currentMs <= it.endMs }
            .let { if (it == -1) words.indexOfLast { w -> currentMs >= w.startMs }.coerceAtLeast(0) else it }

        val chunkSize = 3
        val chunkIndex = activeWordIdx / chunkSize
        val startIdx = chunkIndex * chunkSize
        val endIdx = (startIdx + chunkSize).coerceAtMost(words.size)
        val visibleWords = words.subList(startIdx, endIdx)

        val captionCenterY = if (style == AnimationStyle.VIRAL_2D_EXPLAINER) {
            h * (1480f / 1920f)
        } else {
            h * (1520f / 1920f)
        }
        val textSizePx = if (style == AnimationStyle.VIRAL_2D_EXPLAINER) 82f * u else 74f * u

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setStyle(Paint.Style.STROKE)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            textAlign = Paint.Align.LEFT
        }

        // Measure total line width
        val spaceW = fillPaint.measureText(" ")
        val wordWidths = visibleWords.map { fillPaint.measureText(it.word) }
        val totalW = wordWidths.sum() + spaceW * (visibleWords.size - 1).coerceAtLeast(0)
        val startX = (w - totalW) * 0.5f

        // Background pill for FLAT_VECTOR, TECH_EXPLAINER, and CROSS_SECTION
        if (style == AnimationStyle.FLAT_VECTOR ||
            style == AnimationStyle.TECH_EXPLAINER ||
            style == AnimationStyle.CROSS_SECTION
        ) {
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (style) {
                    AnimationStyle.TECH_EXPLAINER -> 0xEB0B101C.toInt()
                    AnimationStyle.CROSS_SECTION -> 0xEB0B1A2D.toInt()
                    else -> 0xE6101524.toInt()
                }
            }
            val padX = 40f * u
            val padY = 24f * u
            val pillRect = RectF(
                startX - padX,
                captionCenterY - textSizePx - padY * 0.35f,
                startX + totalW + padX,
                captionCenterY + padY
            )
            canvas.drawRoundRect(pillRect, 28f * u, 28f * u, pillPaint)
            if (style == AnimationStyle.TECH_EXPLAINER || style == AnimationStyle.CROSS_SECTION) {
                val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (style == AnimationStyle.CROSS_SECTION) 0x6622D3EE else 0x5500E5FF
                    setStyle(Paint.Style.STROKE)
                    strokeWidth = 3f * u
                }
                canvas.drawRoundRect(pillRect, 28f * u, 28f * u, pillBorder)
            }
        }

        var cursorX = startX
        visibleWords.forEachIndexed { localIdx, wt ->
            val globalIdx = startIdx + localIdx
            val isActive = globalIdx == activeWordIdx
            val isKeyword = isEmphasizedWord(wt.word)

            // 0.12s spring scale-pop (1.22x -> 1.0x) on the currently spoken word
            val popScale = if (isActive) {
                val elapsedSec = ((currentMs - wt.startMs) / 1000.0).toFloat().coerceAtLeast(0f)
                val popFrac = (elapsedSec / 0.12f).coerceIn(0f, 1f)
                1.22f - 0.22f * Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, popFrac)
            } else 1.0f

            canvas.save()
            val wordW = wordWidths[localIdx]
            canvas.scale(popScale, popScale, cursorX + wordW * 0.5f, captionCenterY - textSizePx * 0.3f)

            when (style) {
                AnimationStyle.PAPER_DIORAMA -> {
                    strokePaint.color = Color.BLACK
                    strokePaint.strokeWidth = 14f * u
                    fillPaint.color = when {
                        isActive -> 0xFFFFD166.toInt()
                        isKeyword -> 0xFF38BDF8.toInt()
                        else -> Color.WHITE
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, strokePaint)
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
                AnimationStyle.STICKMAN_WHITEBOARD -> {
                    strokePaint.color = 0xFFFAF6EE.toInt()
                    strokePaint.strokeWidth = 10f * u
                    fillPaint.color = when {
                        isActive -> 0xFFD9383A.toInt()
                        isKeyword -> 0xFF0369A1.toInt()
                        else -> 0xFF1A1D26.toInt()
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, strokePaint)
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
                AnimationStyle.FLAT_VECTOR -> {
                    fillPaint.color = when {
                        isActive -> 0xFFFFE066.toInt()
                        isKeyword -> 0xFF2DD4BF.toInt()
                        else -> 0xFFF8FAFC.toInt()
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
                AnimationStyle.DARK_SUSPENSE -> {
                    strokePaint.color = 0xFF050509.toInt()
                    strokePaint.strokeWidth = 14f * u
                    fillPaint.color = when {
                        isActive -> 0xFFFF4D4D.toInt()
                        isKeyword -> 0xFFFFB300.toInt()
                        else -> 0xFFE2E8F0.toInt()
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, strokePaint)
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
                AnimationStyle.CHIBI_STORYTIME -> {
                    strokePaint.color = if (isActive) 0xFFDB2777.toInt() else 0xFF6D28D9.toInt()
                    strokePaint.strokeWidth = 14f * u
                    fillPaint.color = when {
                        isActive -> 0xFFFFF0F6.toInt()
                        isKeyword -> 0xFFFFE066.toInt()
                        else -> Color.WHITE
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, strokePaint)
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
                AnimationStyle.TECH_EXPLAINER -> {
                    fillPaint.color = when {
                        isActive -> 0xFF00E676.toInt()
                        isKeyword -> 0xFFFFD166.toInt()
                        else -> 0xFFF8FAFC.toInt()
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
                AnimationStyle.CROSS_SECTION -> {
                    fillPaint.color = when {
                        isActive -> 0xFF22D3EE.toInt()
                        isKeyword -> 0xFFFFE066.toInt()
                        else -> 0xFFF8FAFC.toInt()
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
                AnimationStyle.VIRAL_2D_EXPLAINER -> {
                    val dropPaint = Paint(strokePaint).apply {
                        color = 0x88000000.toInt()
                        strokeWidth = 11f * u
                    }
                    canvas.drawText(wt.word, cursorX + 3f * u, captionCenterY + 5f * u, dropPaint)
                    strokePaint.color = Color.BLACK
                    strokePaint.strokeWidth = 10f * u
                    fillPaint.color = when {
                        isActive || isKeyword -> 0xFFFFD600.toInt()
                        else -> Color.WHITE
                    }
                    canvas.drawText(wt.word, cursorX, captionCenterY, strokePaint)
                    canvas.drawText(wt.word, cursorX, captionCenterY, fillPaint)
                }
            }
            canvas.restore()
            cursorX += wordW + spaceW
        }
    }

    private fun drawCoinRain(canvas: Canvas, w: Float, h: Float, u: Float, globalTimeSec: Float) {
        val coinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC300.toInt() }
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB45309.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 4f * u
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF92400E.toInt()
            textSize = 28f * u
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        for (i in 0 until 14) {
            val seedX = ((i * 197 + 60) % 960 + 60) * u
            val speed = 380f + (i % 4) * 90f
            val cy = ((globalTimeSec * speed + i * 180f) % (h + 160f)) - 80f
            val spinScaleX = abs(cos(globalTimeSec * 5f + i)).coerceIn(0.22f, 1f)
            val r = 28f * u
            canvas.save()
            canvas.scale(spinScaleX, 1f, seedX, cy)
            canvas.drawCircle(seedX, cy, r, coinPaint)
            canvas.drawCircle(seedX, cy, r, rimPaint)
            if (spinScaleX > 0.45f) {
                canvas.drawText("₱", seedX, cy + 10f * u, textPaint)
            }
            canvas.restore()
        }
    }

    private fun drawFireSparks(canvas: Canvas, w: Float, h: Float, u: Float, globalTimeSec: Float) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 22) {
            val baseX = ((i * 149 + 80) % 920 + 80) * u
            val progress = ((globalTimeSec * (0.45f + (i % 3) * 0.2f) + i * 0.13f) % 1f)
            val cy = h * 0.85f - progress * h * 0.55f
            val cx = baseX + sin(globalTimeSec * 4f + i) * 35f * u
            val alpha = ((1f - progress) * 230).toInt().coerceIn(0, 255)
            glow.color = if (i % 2 == 0) Color.argb(alpha, 255, 110, 30) else Color.argb(alpha, 255, 215, 70)
            canvas.drawCircle(cx, cy, (14f - progress * 8f) * u, glow)
        }
    }

    private fun drawGiantHandGrab(canvas: Canvas, w: Float, h: Float, u: Float, sceneElapsedSec: Float) {
        val t = Easing.easeOutBack((sceneElapsedSec / 1.1f).coerceIn(0f, 1f))
        val handY = -280f * u + t * (h * 0.36f)
        val cx = w * 0.5f

        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5B28A.toInt() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111827.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 8f * u
        }

        // Forearm descending from top of frame
        val armRect = RectF(cx - 72f * u, -40f * u, cx + 72f * u, handY)
        canvas.drawRect(armRect, armPaint)
        canvas.drawRect(armRect, stroke)

        // Palm & fingers
        val palm = RectF(cx - 125f * u, handY - 20f * u, cx + 125f * u, handY + 130f * u)
        canvas.drawRoundRect(palm, 48f * u, 48f * u, armPaint)
        canvas.drawRoundRect(palm, 48f * u, 48f * u, stroke)

        for (i in -1..2) {
            val fx = cx + (i * 54f - 26f) * u
            val finger = RectF(fx - 20f * u, handY + 95f * u, fx + 20f * u, handY + 195f * u)
            canvas.drawRoundRect(finger, 18f * u, 18f * u, armPaint)
            canvas.drawRoundRect(finger, 18f * u, 18f * u, stroke)
        }
    }

    private fun drawFloatingHouses(
        canvas: Canvas,
        w: Float,
        h: Float,
        u: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float
    ) {
        val housePositions = listOf(0.22f, 0.78f)
        housePositions.forEachIndexed { idx, xRatio ->
            val rise = ((sceneElapsedSec * 90f + idx * 140f) % (h * 0.45f))
            val hx = w * xRatio + sin(globalTimeSec * 2.4f + idx) * 18f * u
            val hy = h * 0.56f - rise
            val size = 86f * u

            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE68A.toInt() }
            val roof = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5E36.toInt() }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1E293B.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 5f * u
            }

            val box = RectF(hx - size * 0.5f, hy, hx + size * 0.5f, hy + size * 0.6f)
            canvas.drawRect(box, body)
            canvas.drawRect(box, stroke)

            val roofPath = Path().apply {
                moveTo(hx, hy - size * 0.45f)
                lineTo(hx - size * 0.65f, hy)
                lineTo(hx + size * 0.65f, hy)
                close()
            }
            canvas.drawPath(roofPath, roof)
            canvas.drawPath(roofPath, stroke)
        }
    }

    private fun drawChalkboardDiagram(
        canvas: Canvas,
        w: Float,
        h: Float,
        u: Float,
        style: AnimationStyle,
        sceneElapsedSec: Float
    ) {
        val progress = (sceneElapsedSec / 1.4f).coerceIn(0f, 1f)
        val strokeColor = if (style == AnimationStyle.STICKMAN_WHITEBOARD) 0xFFD9383A.toInt() else 0xFFFFD166.toInt()

        // Chalkboard backing card if not whiteboard
        if (style != AnimationStyle.STICKMAN_WHITEBOARD) {
            val boardRect = RectF(w * 0.14f, h * 0.14f, w * 0.86f, h * 0.34f)
            canvas.drawRoundRect(boardRect, 20f * u, 20f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDD14362B.toInt() })
            canvas.drawRoundRect(boardRect, 20f * u, 20f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8B5E3C.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 10f * u
            })
        }

        // Progressive upward growth chart arrow via PathMeasure
        val fullPath = Path().apply {
            moveTo(w * 0.22f, h * 0.30f)
            lineTo(w * 0.38f, h * 0.25f)
            lineTo(w * 0.52f, h * 0.27f)
            lineTo(w * 0.74f, h * 0.18f)
            lineTo(w * 0.70f, h * 0.22f)
            moveTo(w * 0.74f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.17f)
        }

        val partial = Path()
        val pm = PathMeasure(fullPath, false)
        var remaining = pm.length * progress
        do {
            val len = pm.length
            if (remaining <= len) {
                pm.getSegment(0f, remaining, partial, true)
                break
            } else {
                pm.getSegment(0f, len, partial, true)
                remaining -= len
            }
        } while (pm.nextContour())

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            setStyle(Paint.Style.STROKE)
            strokeWidth = 10f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(partial, linePaint)
    }

    private fun drawEggCrack(canvas: Canvas, w: Float, h: Float, u: Float, sceneElapsedSec: Float) {
        val cx = w * 0.76f
        val cy = h * 0.58f
        val split = Easing.easeOutBack(((sceneElapsedSec - 0.5f) / 0.8f).coerceIn(0f, 1f))

        val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF8E7.toInt() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1F2937.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 6f * u
        }

        // Peeking yellow chick inside
        if (split > 0.1f) {
            val chickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD166.toInt() }
            val chickY = cy - split * 38f * u
            canvas.drawCircle(cx, chickY, 44f * u, chickPaint)
            canvas.drawCircle(cx, chickY, 44f * u, stroke)
            val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            canvas.drawCircle(cx - 14f * u, chickY - 6f * u, 6f * u, eye)
            canvas.drawCircle(cx + 14f * u, chickY - 6f * u, 6f * u, eye)
        }

        // Bottom shell
        val bottomShell = RectF(cx - 64f * u, cy - 30f * u, cx + 64f * u, cy + 78f * u)
        canvas.drawArc(bottomShell, 0f, 180f, true, eggPaint)
        canvas.drawArc(bottomShell, 0f, 180f, true, stroke)

        // Top shell tilting open
        canvas.save()
        canvas.rotate(-split * 36f, cx - 55f * u, cy)
        val topShell = RectF(cx - 60f * u, cy - 92f * u, cx + 60f * u, cy + 20f * u)
        canvas.drawArc(topShell, 180f, 180f, true, eggPaint)
        canvas.drawArc(topShell, 180f, 180f, true, stroke)
        canvas.restore()
    }
}
