package com.example.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.ActorExpression
import com.example.model.AnimationStyle
import com.example.model.MetaphorVisual
import com.example.model.SceneScript
import com.example.model.StatusPill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object MetaphorVisualRenderer {

    fun resolveMetaphorVisual(scene: SceneScript, style: AnimationStyle): MetaphorVisual {
        if (scene.metaphorVisual != MetaphorVisual.NONE) return scene.metaphorVisual
        if (style != AnimationStyle.VIRAL_2D_EXPLAINER) return MetaphorVisual.NONE
        return when ((scene.sceneId - 1).coerceAtLeast(0) % 5) {
            0 -> MetaphorVisual.SUNBURST_SPOTLIGHT
            1 -> MetaphorVisual.CHOMP_CHAIN
            2 -> MetaphorVisual.TISSUE_CROSS_SECTION
            3 -> MetaphorVisual.BOILING_POT_PROCESS
            else -> MetaphorVisual.TIME_LAPSE_CLOCK
        }
    }

    fun isDiagramAlongsideCharacter(metaphor: MetaphorVisual): Boolean {
        return metaphor == MetaphorVisual.CHOMP_CHAIN ||
            metaphor == MetaphorVisual.TISSUE_CROSS_SECTION ||
            metaphor == MetaphorVisual.TIME_LAPSE_CLOCK ||
            metaphor == MetaphorVisual.BOILING_POT_PROCESS
    }

    /**
     * Draws the rotating 16-ray radial sunburst spotlight behind the hero character/prop.
     */
    fun drawSunburstBackground(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneScript,
        globalTimeSec: Float
    ) {
        val u = w / 1080f
        val isDanger = scene.statusPill == StatusPill.PROBLEM ||
            scene.expression == ActorExpression.PANIC_SWEAT_FIRE ||
            scene.expression == ActorExpression.ANGRY_EVIL ||
            scene.expression == ActorExpression.TONGUE_STING

        val baseColor = if (isDanger) 0xFFDC2626.toInt() else 0xFF0284C7.toInt()
        val rayColorA = if (isDanger) 0xFFFB923C.toInt() else 0xFF22D3EE.toInt()
        val rayColorB = if (isDanger) 0xFFEA580C.toInt() else 0xFF0EA5E9.toInt()
        val coreGlowColor = if (isDanger) 0xFFFDE047.toInt() else 0xFFE0F2FE.toInt()

        canvas.drawColor(baseColor)

        val cx = w * 0.5f
        val cy = h * 0.44f
        val radius = h * 0.95f
        val rotDeg = globalTimeSec * 14f

        canvas.save()
        canvas.rotate(rotDeg, cx, cy)
        val rayCount = 16
        val stepDeg = 360f / rayCount
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until rayCount) {
            rayPaint.color = if (i % 2 == 0) rayColorA else rayColorB
            val a0 = Math.toRadians((i * stepDeg).toDouble())
            val a1 = Math.toRadians(((i + 0.56f) * stepDeg).toDouble())
            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius)
                lineTo(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius)
                close()
            }
            canvas.drawPath(path, rayPaint)
        }
        canvas.restore()

        // Bright center spotlight halo behind character/prop
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, 460f * u,
                intArrayOf(coreGlowColor, (coreGlowColor and 0x00FFFFFF) or 0x55000000, 0x00000000),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, 460f * u, haloPaint)
    }

    /**
     * Draws animated 2D visual metaphor diagrams in the upper-right / upper-center explainer stage.
     */
    fun drawDiagram(
        canvas: Canvas,
        w: Float,
        h: Float,
        metaphor: MetaphorVisual,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float
    ) {
        if (!isDiagramAlongsideCharacter(metaphor)) return
        val u = w / 1080f

        val cardRect = RectF(w * 0.38f, h * 0.145f, w * 0.95f, h * 0.455f)
        val enterScale = Easing.easeOutBack((sceneElapsedSec / 0.42f).coerceIn(0f, 1f)).coerceAtLeast(0.05f)
        val cx = cardRect.centerX()
        val cy = cardRect.centerY()

        canvas.save()
        canvas.scale(enterScale, enterScale, cx, cy)

        // Soft drop shadow + crisp dark 6px cartoon frame
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            maskFilter = BlurMaskFilter((16f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(cardRect.left + 8f * u, cardRect.top + 12f * u, cardRect.right + 8f * u, cardRect.bottom + 12f * u),
            28f * u, 28f * u, shadowPaint
        )
        canvas.drawRoundRect(
            cardRect, 28f * u, 28f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF5F8FAFC.toInt() }
        )
        val frameStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 6f * u
        }
        canvas.drawRoundRect(cardRect, 28f * u, 28f * u, frameStroke)

        when (metaphor) {
            MetaphorVisual.CHOMP_CHAIN -> drawChompChain(canvas, cardRect, u, scene, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.TISSUE_CROSS_SECTION -> drawTissueCrossSection(canvas, cardRect, u, sceneElapsedSec, sceneDurationSec, globalTimeSec, frameStroke)
            MetaphorVisual.TIME_LAPSE_CLOCK -> drawTimeLapseClock(canvas, cardRect, u, sceneElapsedSec, globalTimeSec, frameStroke)
            MetaphorVisual.BOILING_POT_PROCESS -> drawBoilingPotProcess(canvas, cardRect, u, globalTimeSec, frameStroke)
            MetaphorVisual.SUNBURST_SPOTLIGHT, MetaphorVisual.NONE -> Unit
        }

        canvas.restore()
    }

    private fun drawChompChain(
        canvas: Canvas,
        box: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        val label = when {
            scene.tagalogNarration.contains("pera", ignoreCase = true) ||
                scene.tagalogNarration.contains("ipon", ignoreCase = true) -> "MONEY CHAIN"
            scene.tagalogNarration.contains("data", ignoreCase = true) ||
                scene.tagalogNarration.contains("wifi", ignoreCase = true) -> "DATA CHAIN"
            else -> "PROTEIN CHAIN"
        }
        drawDiagramHeader(canvas, box, u, label, 0xFF2563EB.toInt())

        val beadColors = intArrayOf(
            0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF10B981.toInt(),
            0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt()
        )
        val beadCount = 6
        val startX = box.left + 72f * u
        val endX = box.right - 72f * u
        val baseY = box.centerY() + 36f * u
        val chompProg = ((sceneElapsedSec * 0.42f) % 1.15f).coerceIn(0f, 1.05f)
        val chompX = startX + (endX - startX) * chompProg

        // Connecting bonds
        val bondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF334155.toInt()
            strokeWidth = 10f * u
            strokeCap = Paint.Cap.ROUND
        }
        val beadPoints = Array(beadCount) { idx ->
            val frac = idx / (beadCount - 1f)
            val bx = startX + (endX - startX) * frac
            val by = baseY + sin(frac * PI.toFloat() * 2f + globalTimeSec * 2.5f) * 34f * u
            bx to by
        }
        for (i in 0 until beadCount - 1) {
            val (x0, y0) = beadPoints[i]
            val (x1, y1) = beadPoints[i + 1]
            if (x1 > chompX - 18f * u) {
                canvas.drawLine(x0, y0, x1, y1, bondPaint)
            }
        }

        // Multi-colored beads (pop off as Pac-Man enzymes pass)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FFFFFF.toInt() }
        for (i in 0 until beadCount) {
            val (bx, by) = beadPoints[i]
            if (bx <= chompX - 12f * u) {
                // Eaten sparkle pop
                val dist = chompX - bx
                if (dist < 75f * u) {
                    fillPaint.color = beadColors[i % beadColors.size]
                    canvas.drawCircle(bx - 12f * u, by - dist * 0.4f, 6f * u, fillPaint)
                    canvas.drawCircle(bx + 10f * u, by + dist * 0.3f, 5f * u, fillPaint)
                }
                continue
            }
            val r = 29f * u
            fillPaint.color = beadColors[i % beadColors.size]
            canvas.drawCircle(bx, by, r, fillPaint)
            canvas.drawCircle(bx - 8f * u, by - 9f * u, 8f * u, shinePaint)
            canvas.drawCircle(bx, by, r, outline)
        }

        // Two Pac-Man enzyme spheres chomping forward
        val chompMouth = 12f + abs(sin(globalTimeSec * 14f)) * 34f
        fun drawEnzyme(ex: Float, ey: Float, color: Int, radius: Float) {
            val rect = RectF(ex - radius, ey - radius, ex + radius, ey + radius)
            fillPaint.color = color
            canvas.drawArc(rect, chompMouth, 360f - chompMouth * 2f, true, fillPaint)
            canvas.drawArc(rect, chompMouth, 360f - chompMouth * 2f, true, outline)
            canvas.drawCircle(ex + radius * 0.12f, ey - radius * 0.44f, 6.5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.BLACK })
        }

        val chompY = baseY + sin((chompProg * PI.toFloat() * 2f) + globalTimeSec * 2.5f) * 34f * u
        drawEnzyme(chompX, chompY, 0xFFFFD600.toInt(), 42f * u)
        drawEnzyme((chompX - 78f * u).coerceAtLeast(box.left + 44f * u), chompY - 46f * u, 0xFF22D3EE.toInt(), 28f * u)
    }

    private fun drawTissueCrossSection(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "PROTECTIVE BARRIER", 0xFFDC2626.toInt())

        val blockL = box.left + 46f * u
        val blockR = box.right - 46f * u
        val blockTop = box.top + 116f * u
        val blockBot = box.bottom - 38f * u
        val totalH = blockBot - blockTop

        val dissolve = (sceneElapsedSec / (sceneDurationSec * 0.72f).coerceAtLeast(0.8f)).coerceIn(0f, 1f)

        // Deep tissue base + nerve layer
        val deepRect = RectF(blockL, blockTop + totalH * 0.45f, blockR, blockBot)
        canvas.drawRoundRect(deepRect, 14f * u, 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFCA5A5.toInt() })
        canvas.drawRoundRect(deepRect, 14f * u, 14f * u, outline)

        // Cellular middle bricks
        val cellTop = blockTop + totalH * 0.22f
        val cellBot = blockTop + totalH * 0.48f
        val cellCount = 5
        val cellW = (blockR - blockL) / cellCount
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF43F5E.toInt() }
        for (i in 0 until cellCount) {
            val cr = RectF(blockL + i * cellW + 3f * u, cellTop, blockL + (i + 1) * cellW - 3f * u, cellBot)
            canvas.drawRoundRect(cr, 8f * u, 8f * u, cellPaint)
            canvas.drawRoundRect(cr, 8f * u, 8f * u, outline)
        }

        // Top protective mucus/shield barrier dissolving into gaps
        val shieldH = totalH * 0.20f * (1f - dissolve * 0.72f)
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() }
        for (i in 0 until cellCount) {
            if (dissolve > 0.35f && (i == 1 || i == 3)) continue // dissolved breach gaps!
            val sr = RectF(blockL + i * cellW + 2f * u, blockTop, blockL + (i + 1) * cellW - 2f * u, blockTop + shieldH.coerceAtLeast(6f * u))
            canvas.drawRoundRect(sr, 7f * u, 7f * u, shieldPaint)
            canvas.drawRoundRect(sr, 7f * u, 7f * u, outline)
        }

        // Falling yellow acid/enzyme droplets penetrating breaches
        val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() }
        for (idx in listOf(1, 3)) {
            val dx = blockL + (idx + 0.5f) * cellW
            val fallFrac = ((globalTimeSec * 0.9f + idx * 0.35f) % 1f)
            val dy = (blockTop - 32f * u) + fallFrac * (totalH * 0.55f)
            val drop = Path().apply {
                moveTo(dx, dy - 16f * u)
                quadTo(dx + 14f * u, dy + 8f * u, dx, dy + 16f * u)
                quadTo(dx - 14f * u, dy + 8f * u, dx, dy - 16f * u)
            }
            canvas.drawPath(drop, dropPaint)
            canvas.drawPath(drop, outline)
        }

        // Glowing lightning-bolt nerve sparks pulsing underneath
        val sparkAlpha = (140 + (115 * abs(sin(globalTimeSec * 12f)))).toInt().coerceIn(80, 255)
        val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(sparkAlpha, 250, 204, 21)
            setStyle(Paint.Style.STROKE)
            strokeWidth = 7f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val nerveY = deepRect.centerY()
        for (idx in listOf(1, 3)) {
            val sx = blockL + (idx + 0.5f) * cellW
            val bolt = Path().apply {
                moveTo(sx - 10f * u, nerveY - 36f * u)
                lineTo(sx + 14f * u, nerveY - 8f * u)
                lineTo(sx - 8f * u, nerveY - 2f * u)
                lineTo(sx + 18f * u, nerveY + 34f * u)
            }
            canvas.drawPath(bolt, boltPaint)
        }
    }

    private fun drawTimeLapseClock(
        canvas: Canvas,
        box: RectF,
        u: Float,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "TIME-LAPSE", 0xFF0284C7.toInt())

        val cx = box.centerX()
        val cy = box.centerY() + 28f * u
        val r = minOf(box.width(), box.height()) * 0.30f

        // Swept speed arc behind hands
        val sweepDeg = (sceneElapsedSec * 360f) % 360f
        canvas.drawArc(
            RectF(cx - r * 1.18f, cy - r * 1.18f, cx + r * 1.18f, cy + r * 1.18f),
            sweepDeg - 100f, 100f, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x6638BDF8
                setStyle(Paint.Style.STROKE)
                strokeWidth = 14f * u
                strokeCap = Paint.Cap.ROUND
            }
        )

        // Clock casing & face
        canvas.drawCircle(cx, cy, r + 14f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0EA5E9.toInt() })
        canvas.drawCircle(cx, cy, r + 14f * u, outline)
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(cx, cy, r, outline)

        // 12 hour tick marks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E293B.toInt()
            strokeWidth = 5f * u
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30.0))
            canvas.drawLine(
                cx + cos(a).toFloat() * (r * 0.78f),
                cy + sin(a).toFloat() * (r * 0.78f),
                cx + cos(a).toFloat() * (r * 0.90f),
                cy + sin(a).toFloat() * (r * 0.90f),
                tickPaint
            )
        }

        // Rapidly spinning hour & minute hands
        val minAngle = Math.toRadians((globalTimeSec * 420.0 - 90.0))
        val hourAngle = Math.toRadians((globalTimeSec * 70.0 - 90.0))
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            strokeWidth = 9f * u
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, cx + cos(hourAngle).toFloat() * (r * 0.48f), cy + sin(hourAngle).toFloat() * (r * 0.48f), handPaint)
        handPaint.color = 0xFFEF4444.toInt()
        handPaint.strokeWidth = 6f * u
        canvas.drawLine(cx, cy, cx + cos(minAngle).toFloat() * (r * 0.72f), cy + sin(minAngle).toFloat() * (r * 0.72f), handPaint)
        canvas.drawCircle(cx, cy, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1018.toInt() })

        // Radial sparkle bursts around clock rim
        val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD600.toInt() }
        for (i in 0 until 4) {
            val pulse = 0.75f + 0.35f * abs(sin(globalTimeSec * 7f + i))
            val ang = Math.toRadians((45.0 + i * 90.0))
            val sx = cx + cos(ang).toFloat() * (r + 42f * u)
            val sy = cy + sin(ang).toFloat() * (r + 42f * u)
            val s = 18f * u * pulse
            val star = Path().apply {
                moveTo(sx, sy - s)
                lineTo(sx + s * 0.32f, sy - s * 0.32f)
                lineTo(sx + s, sy)
                lineTo(sx + s * 0.32f, sy + s * 0.32f)
                lineTo(sx, sy + s)
                lineTo(sx - s * 0.32f, sy + s * 0.32f)
                lineTo(sx - s, sy)
                lineTo(sx - s * 0.32f, sy - s * 0.32f)
                close()
            }
            canvas.drawPath(star, sparkPaint)
            canvas.drawPath(star, Paint(outline).apply { strokeWidth = 3f * u })
        }
    }

    private fun drawBoilingPotProcess(
        canvas: Canvas,
        box: RectF,
        u: Float,
        globalTimeSec: Float,
        outline: Paint
    ) {
        drawDiagramHeader(canvas, box, u, "ACTIVE PROCESS", 0xFFEA580C.toInt())

        val cx = box.centerX()
        val stoveY = box.bottom - 38f * u
        val potW = box.width() * 0.56f
        val potH = box.height() * 0.34f
        val potBot = stoveY - 32f * u
        val potTop = potBot - potH

        // Animated stove flames under pot
        val flameOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5722.toInt() }
        val flameInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD600.toInt() }
        for (i in -2..2) {
            val fx = cx + i * 38f * u
            val flicker = 1f + 0.25f * sin(globalTimeSec * 16f + i * 1.4f)
            val fPath = Path().apply {
                moveTo(fx - 18f * u, stoveY)
                quadTo(fx - 12f * u, stoveY - 34f * u * flicker, fx, stoveY - 46f * u * flicker)
                quadTo(fx + 12f * u, stoveY - 34f * u * flicker, fx + 18f * u, stoveY)
                close()
            }
            canvas.drawPath(fPath, flameOuter)
            canvas.drawCircle(fx, stoveY - 12f * u, 9f * u * flicker, flameInner)
        }
        // Stove burner bar
        canvas.drawRoundRect(
            RectF(cx - potW * 0.56f, stoveY, cx + potW * 0.56f, stoveY + 14f * u),
            6f * u, 6f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF334155.toInt() }
        )

        // Animated wavy rising steam paths
        val steamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99CBD5E1.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 7f * u
            strokeCap = Paint.Cap.ROUND
        }
        for (i in -1..1) {
            val sx = cx + i * 54f * u
            val phase = globalTimeSec * 4.5f + i * 1.2f
            val steam = Path().apply {
                moveTo(sx, potTop - 16f * u)
                cubicTo(
                    sx - 20f * u * sin(phase), potTop - 48f * u,
                    sx + 20f * u * sin(phase), potTop - 82f * u,
                    sx + sin(phase) * 10f * u, potTop - 112f * u
                )
            }
            canvas.drawPath(steam, steamPaint)
        }

        // Bubbling particles popping from liquid surface
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE047.toInt() }
        for (i in 0 until 6) {
            val prog = ((globalTimeSec * (0.8f + (i % 3) * 0.25f) + i * 0.19f) % 1f)
            val bx = cx + (i - 2.5f) * 44f * u + sin(globalTimeSec * 5f + i) * 8f * u
            val by = potTop - prog * 68f * u
            val br = (14f - prog * 6f) * u
            canvas.drawCircle(bx, by, br, bubblePaint)
            canvas.drawCircle(bx, by, br, Paint(outline).apply { strokeWidth = 3f * u })
        }

        // Steel pot body + handles + shimmering broth rim
        val potRect = RectF(cx - potW * 0.5f, potTop, cx + potW * 0.5f, potBot)
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 10f * u
        }
        canvas.drawArc(RectF(potRect.left - 24f * u, potTop + 26f * u, potRect.left + 8f * u, potTop + 66f * u), 90f, 180f, false, handlePaint)
        canvas.drawArc(RectF(potRect.right - 8f * u, potTop + 26f * u, potRect.right + 24f * u, potTop + 66f * u), -90f, 180f, false, handlePaint)

        val potFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                potRect.left, potRect.top, potRect.right, potRect.top,
                intArrayOf(0xFFE2E8F0.toInt(), 0xFF94A3B8.toInt(), 0xFF64748B.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(potRect, 22f * u, 22f * u, potFill)
        canvas.drawRoundRect(potRect, 22f * u, 22f * u, outline)

        // Simmering liquid oval at top rim
        val liquidRect = RectF(potRect.left + 10f * u, potTop - 14f * u, potRect.right - 10f * u, potTop + 16f * u)
        canvas.drawOval(liquidRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() })
        canvas.drawOval(liquidRect, outline)
    }

    private fun drawDiagramHeader(
        canvas: Canvas,
        box: RectF,
        u: Float,
        text: String,
        pillColor: Int
    ) {
        val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }
        val tw = tPaint.measureText(text)
        val pw = (tw + 48f * u).coerceAtMost(box.width() - 32f * u)
        val ph = 46f * u
        val cx = box.centerX()
        val cy = box.top + 48f * u
        val rect = RectF(cx - pw * 0.5f, cy - ph * 0.5f, cx + pw * 0.5f, cy + ph * 0.5f)
        canvas.drawRoundRect(rect, ph * 0.5f, ph * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor })
        canvas.drawRoundRect(rect, ph * 0.5f, ph * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 4f * u
        })
        val fm = tPaint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) * 0.5f, tPaint)
    }
}
