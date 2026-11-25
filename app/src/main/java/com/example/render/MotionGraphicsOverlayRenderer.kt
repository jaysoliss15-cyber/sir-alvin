package com.example.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.AnimationStyle
import com.example.model.HudWidget
import com.example.model.ProductionMode
import com.example.model.SceneScript
import com.example.model.StatusPill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object MotionGraphicsOverlayRenderer {

    fun drawOverlays(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneScript,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        productionMode: ProductionMode = ProductionMode.FULL_AI_STORY
    ) {
        val u = w / 1080f
        val autoHudEnabled = style == AnimationStyle.TECH_EXPLAINER ||
            style == AnimationStyle.CROSS_SECTION ||
            productionMode == ProductionMode.CLIP_AUTO_EDITOR ||
            productionMode == ProductionMode.CROSS_SECTION_EXPLAINER

        // Ensure rich HUD elements are active in Tech Explainer, Cutaway, and Clip Auto-HUD modes
        val effectivePill = if (scene.statusPill != StatusPill.NONE) {
            scene.statusPill
        } else if (autoHudEnabled) {
            when ((scene.sceneId - 1).coerceAtLeast(0) % 4) {
                0 -> StatusPill.GOAL
                1 -> StatusPill.PROBLEM
                2 -> StatusPill.FIX
                else -> StatusPill.SECRET
            }
        } else StatusPill.NONE

        val effectiveCallout = scene.calloutBanner.ifBlank {
            if (autoHudEnabled) {
                when (effectivePill) {
                    StatusPill.PROBLEM -> "MALAKING MALI!"
                    StatusPill.FIX -> "ITO ANG SOLUSYON"
                    StatusPill.SECRET -> "90% SIKRETO"
                    else -> "ALAMIN ITO!"
                }
            } else ""
        }

        val effectiveWidget = if (scene.hudWidget != HudWidget.NONE) {
            scene.hudWidget
        } else if (autoHudEnabled) {
            when ((scene.sceneId - 1).coerceAtLeast(0) % 3) {
                0 -> HudWidget.PROGRESS_GAUGE
                1 -> HudWidget.WAVEFORM_GRAPH
                else -> HudWidget.ANIMATED_CHECKLIST
            }
        } else HudWidget.NONE

        // 1. Top Status Pill / VIRAL_2D_EXPLAINER Topic Pill Badge (y = 148-160px @ 1080x1920)
        if (style == AnimationStyle.VIRAL_2D_EXPLAINER) {
            val badgeText = scene.topicPillLabel.ifBlank {
                scene.calloutBanner.ifBlank {
                    when (effectivePill) {
                        StatusPill.PROBLEM -> "BAKIT DELIKADO?"
                        StatusPill.FIX -> "ITO ANG SOLUSYON"
                        StatusPill.SECRET -> "SIKRETO NG PRO"
                        StatusPill.GOAL -> "SCIENCE EXPLAINED"
                        StatusPill.NONE -> when ((scene.sceneId - 1).coerceAtLeast(0) % 3) {
                            0 -> "ALAM MO BA?"
                            1 -> "PAANO NANGYARI?"
                            else -> "PRO TIP"
                        }
                    }
                }
            }.uppercase()
            drawTopicPillBadge(canvas, w, u, badgeText, effectivePill, scene.sceneId, sceneElapsedSec)
        } else if (effectivePill != StatusPill.NONE) {
            drawStatusPill(canvas, w, u, effectivePill, sceneElapsedSec)
        }

        // 2. Tilted Pop-In Callout Banner + PathMeasure curved pointer arrow
        if (effectiveCallout.isNotBlank() && style != AnimationStyle.VIRAL_2D_EXPLAINER) {
            drawCalloutBanner(canvas, w, h, u, scene.sceneId, effectiveCallout, sceneElapsedSec)
        }

        // 3. Lower Safe-Zone Live Animated HUD Widget (above karaoke captions)
        if (effectiveWidget != HudWidget.NONE) {
            drawHudWidget(
                canvas = canvas,
                w = w,
                h = h,
                u = u,
                widget = effectiveWidget,
                scene = scene,
                sceneElapsedSec = sceneElapsedSec,
                sceneDurationSec = sceneDurationSec,
                globalTimeSec = globalTimeSec
            )
        }
    }

    private fun drawTopicPillBadge(
        canvas: Canvas,
        w: Float,
        u: Float,
        label: String,
        pill: StatusPill,
        sceneId: Int,
        sceneElapsedSec: Float
    ) {
        val popT = (sceneElapsedSec / 0.35f).coerceIn(0f, 1f)
        val scale = Easing.easeOutBack(popT).coerceAtLeast(0.1f)
        val cy = 148f * u

        val bgColor = when (pill) {
            StatusPill.PROBLEM -> 0xFFDC2626.toInt()
            StatusPill.FIX -> 0xFF16A34A.toInt()
            StatusPill.SECRET -> 0xFF2563EB.toInt()
            StatusPill.GOAL -> 0xFF0284C7.toInt()
            StatusPill.NONE -> if (sceneId % 2 == 1) 0xFF2563EB.toInt() else 0xFF16A34A.toInt()
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val textW = textPaint.measureText(label)
        val padX = 44f * u
        val pillW = (textW + padX * 2f).coerceAtMost(w * 0.86f)
        val pillH = 72f * u
        val left = (w - pillW) * 0.5f
        val rect = RectF(left, cy - pillH * 0.5f, left + pillW, cy + pillH * 0.5f)

        canvas.save()
        canvas.scale(scale, scale, w * 0.5f, cy)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
            maskFilter = BlurMaskFilter((10f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(rect.left + 4f * u, rect.top + 8f * u, rect.right + 4f * u, rect.bottom + 8f * u),
            pillH * 0.5f, pillH * 0.5f, shadowPaint
        )

        canvas.drawRoundRect(rect, pillH * 0.5f, pillH * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
        canvas.drawRoundRect(rect, pillH * 0.5f, pillH * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 6f * u
        })

        val fm = textPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(label, w * 0.5f - textW * 0.5f, textY, textPaint)
        canvas.restore()
    }

    private fun drawStatusPill(
        canvas: Canvas,
        w: Float,
        u: Float,
        pill: StatusPill,
        sceneElapsedSec: Float
    ) {
        val dropT = (sceneElapsedSec / 0.45f).coerceIn(0f, 1f)
        val spring = Easing.springBounce(dropT)
        val startY = -70f * u
        val targetY = 160f * u
        val cy = startY + (targetY - startY) * spring

        val (bgColor, textColor, dotColor) = when (pill) {
            StatusPill.GOAL -> Triple(0xFF1E2229.toInt(), Color.WHITE, 0xFF00E5FF.toInt())
            StatusPill.PROBLEM -> Triple(0xFFFF5722.toInt(), Color.WHITE, 0xFFFFE082.toInt())
            StatusPill.FIX -> Triple(0xFF00C853.toInt(), Color.WHITE, 0xFFB9F6CA.toInt())
            StatusPill.SECRET -> Triple(0xFFFFB300.toInt(), 0xFF111827.toInt(), 0xFF111827.toInt())
            StatusPill.NONE -> return
        }

        val label = pill.name
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 34f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val textW = textPaint.measureText(label)
        val dotSpace = 34f * u
        val padX = 34f * u
        val pillW = padX * 2f + dotSpace + textW
        val pillH = 66f * u
        val left = (w - pillW) * 0.5f
        val rect = RectF(left, cy - pillH * 0.5f, left + pillW, cy + pillH * 0.5f)

        // Drop shadow
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
            maskFilter = BlurMaskFilter((14f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + 8f * u, rect.right, rect.bottom + 8f * u),
            33f * u, 33f * u, shadow
        )

        // Pill fill & crisp border
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(rect, 33f * u, 33f * u, fill)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            setStyle(Paint.Style.STROKE)
            strokeWidth = 3f * u
        }
        canvas.drawRoundRect(rect, 33f * u, 33f * u, border)

        // Leading pulse dot
        canvas.drawCircle(left + padX + 8f * u, cy, 9f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor })

        val fm = textPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(label, left + padX + dotSpace, textY, textPaint)
    }

    private fun drawCalloutBanner(
        canvas: Canvas,
        w: Float,
        h: Float,
        u: Float,
        sceneId: Int,
        bannerText: String,
        sceneElapsedSec: Float
    ) {
        val rawT = (sceneElapsedSec / 0.35f).coerceIn(0f, 1f)
        // Kinetic scale pop: 0.0 -> 1.15 -> 1.0 over first 0.35s
        val popScale = if (rawT <= 0.6f) {
            1.15f * Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, rawT / 0.6f)
        } else {
            1.15f - 0.15f * Easing.smoothstep((rawT - 0.6f) / 0.4f)
        }.coerceAtLeast(0.01f)

        val onRight = sceneId % 2 == 1
        val tiltDeg = if (onRight) -3.8f else 3.8f
        val cx = if (onRight) w * 0.72f else w * 0.28f
        val cy = h * 0.235f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            textSize = 38f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textW = textPaint.measureText(bannerText)
        val boxW = (textW + 64f * u).coerceIn(220f * u, w * 0.56f)
        val boxH = 82f * u
        val cardRect = RectF(cx - boxW * 0.5f, cy - boxH * 0.5f, cx + boxW * 0.5f, cy + boxH * 0.5f)

        canvas.save()
        canvas.scale(popScale, popScale, cx, cy)
        canvas.rotate(tiltDeg, cx, cy)

        // Offset crisp comic drop shadow
        val shadowRect = RectF(
            cardRect.left + 10f * u,
            cardRect.top + 12f * u,
            cardRect.right + 10f * u,
            cardRect.bottom + 12f * u
        )
        canvas.drawRoundRect(shadowRect, 18f * u, 18f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() })

        // Vibrant Amber/Yellow fill with thick border
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                0xFFFFE066.toInt(), 0xFFFFB300.toInt(), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 18f * u, 18f * u, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 6f * u
        }
        canvas.drawRoundRect(cardRect, 18f * u, 18f * u, borderPaint)

        val fm = textPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(bannerText, cx, textY, textPaint)
        canvas.restore()

        // Animated curved PathMeasure pointer arrow pointing toward the active character
        val arrowProgress = ((sceneElapsedSec - 0.12f) / 0.42f).coerceIn(0f, 1f)
        if (arrowProgress > 0.02f) {
            val startX = cx
            val startY = cy + boxH * 0.55f
            val endX = w * 0.52f + (if (onRight) 44f * u else -44f * u)
            val endY = h * 0.34f
            val ctrlX = if (onRight) cx + 22f * u else cx - 22f * u
            val ctrlY = (startY + endY) * 0.55f

            val arrowPath = Path().apply {
                moveTo(startX, startY)
                quadTo(ctrlX, ctrlY, endX, endY)
            }
            val pm = PathMeasure(arrowPath, false)
            val partial = Path()
            val len = pm.length
            val drawLen = len * arrowProgress
            pm.getSegment(0f, drawLen, partial, true)

            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD166.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 8f * u
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(partial, arrowPaint)

            // Draw arrowhead at current tip
            val pos = FloatArray(2)
            val tan = FloatArray(2)
            pm.getPosTan(drawLen, pos, tan)
            if (arrowProgress > 0.3f) {
                val angle = atan2(tan[1].toDouble(), tan[0].toDouble())
                val headLen = 22f * u
                val headPath = Path().apply {
                    moveTo(pos[0], pos[1])
                    lineTo(
                        pos[0] - headLen * cos(angle - 0.52).toFloat(),
                        pos[1] - headLen * sin(angle - 0.52).toFloat()
                    )
                    moveTo(pos[0], pos[1])
                    lineTo(
                        pos[0] - headLen * cos(angle + 0.52).toFloat(),
                        pos[1] - headLen * sin(angle + 0.52).toFloat()
                    )
                }
                canvas.drawPath(headPath, arrowPaint)
            }
        }
    }

    private fun drawHudWidget(
        canvas: Canvas,
        w: Float,
        h: Float,
        u: Float,
        widget: HudWidget,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float
    ) {
        val cardLeft = w * 0.08f
        val cardRight = w * 0.92f
        val cardTop = h * (1120f / 1920f)
        val cardBottom = h * (1420f / 1920f)
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // Slide-up + fade entrance over first 0.28s
        val enterT = Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, (sceneElapsedSec / 0.28f).coerceIn(0f, 1f))
        canvas.save()
        canvas.translate(0f, (1f - enterT) * 40f * u)

        // Semi-transparent dark glass card
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDE0E1422.toInt() }
        canvas.drawRoundRect(cardRect, 24f * u, 24f * u, glassPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x7700E5FF
            setStyle(Paint.Style.STROKE)
            strokeWidth = 3.5f * u
        }
        canvas.drawRoundRect(cardRect, 24f * u, 24f * u, borderPaint)

        when (widget) {
            HudWidget.PROGRESS_GAUGE -> drawProgressGauge(
                canvas, cardRect, u, scene, sceneElapsedSec, sceneDurationSec
            )
            HudWidget.WAVEFORM_GRAPH -> drawWaveformGraph(
                canvas, cardRect, u, scene, sceneElapsedSec, sceneDurationSec, globalTimeSec
            )
            HudWidget.ANIMATED_CHECKLIST -> drawAnimatedChecklist(
                canvas, cardRect, u, scene, sceneElapsedSec
            )
            HudWidget.NONE -> Unit
        }
        canvas.restore()
    }

    private fun drawProgressGauge(
        canvas: Canvas,
        card: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float
    ) {
        val animFrac = Easing.cubicBezier(
            0.22f, 1f, 0.36f, 1f,
            (sceneElapsedSec / (sceneDurationSec * 0.80f).coerceAtLeast(0.5f)).coerceIn(0f, 1f)
        )
        val fromVal = scene.hudFromValue.coerceIn(0, 100)
        val toVal = scene.hudToValue.coerceIn(0, 100)
        val currentVal = fromVal + (toVal - fromVal) * animFrac
        val pctInt = currentVal.roundToInt().coerceIn(0, 100)

        val barColor = when {
            currentVal < 40f -> 0xFFFF5722.toInt()
            currentVal < 70f -> 0xFFFFB300.toInt()
            else -> 0xFF00E676.toInt()
        }

        val label = scene.hudLabel.ifBlank { "POWER METER" }.uppercase()
        val pad = 34f * u

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            textSize = 30f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        canvas.drawText(label, card.left + pad, card.top + 64f * u, titlePaint)

        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = barColor
            textSize = 52f * u
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("$pctInt%", card.right - pad, card.top + 74f * u, pctPaint)

        // Track & animated fill bar
        val trackTop = card.top + 115f * u
        val trackBot = trackTop + 42f * u
        val trackRect = RectF(card.left + pad, trackTop, card.right - pad, trackBot)
        canvas.drawRoundRect(trackRect, 21f * u, 21f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F2937.toInt() })

        val fillW = (trackRect.width() * (currentVal / 100f)).coerceAtLeast(24f * u)
        val fillRect = RectF(trackRect.left, trackRect.top, (trackRect.left + fillW).coerceAtMost(trackRect.right), trackRect.bottom)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (barColor and 0x00FFFFFF) or 0x55000000
            maskFilter = BlurMaskFilter((14f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(fillRect, 21f * u, 21f * u, glow)
        canvas.drawRoundRect(fillRect, 21f * u, 21f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor })

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 24f * u
        }
        val statusMsg = if (pctInt >= 50) "OPTIMAL LEVEL" else "WARNING: LOW OUTPUT"
        canvas.drawText(statusMsg, card.left + pad, card.bottom - 34f * u, subPaint)
    }

    private fun drawWaveformGraph(
        canvas: Canvas,
        card: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float
    ) {
        val pad = 32f * u
        val label = scene.hudLabel.ifBlank { "SIGNAL WAVEFORM" }.uppercase()
        val isStableTarget = scene.hudToValue >= 50
        val morphT = if (isStableTarget) {
            Easing.smoothstep((sceneElapsedSec / (sceneDurationSec * 0.65f).coerceAtLeast(0.5f)).coerceIn(0f, 1f))
        } else 0f

        val waveColor = if (isStableTarget && morphT > 0.45f) 0xFF00E676.toInt() else 0xFFFF5722.toInt()
        val badgeText = if (isStableTarget && morphT > 0.45f) "STABLE" else "UNSTABLE"

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            textSize = 28f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label, card.left + pad, card.top + 56f * u, titlePaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = waveColor
            textSize = 26f * u
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(badgeText, card.right - pad, card.top + 56f * u, badgePaint)

        // Inner graph frame
        val gLeft = card.left + pad
        val gRight = card.right - pad
        val gTop = card.top + 82f * u
        val gBot = card.bottom - 26f * u
        val gMidY = (gTop + gBot) * 0.5f
        val gAmp = (gBot - gTop) * 0.36f

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2294A3B8
            strokeWidth = 2f * u
        }
        canvas.drawLine(gLeft, gMidY, gRight, gMidY, gridPaint)

        val wavePath = Path()
        val steps = 48
        val erraticWeight = 1f - morphT
        for (i in 0..steps) {
            val frac = i / steps.toFloat()
            val x = gLeft + (gRight - gLeft) * frac
            val smoothSine = sin(frac * PI.toFloat() * 4f - globalTimeSec * 7f) * gAmp * 0.78f
            val jagged = (
                sin(frac * 29f + globalTimeSec * 19f) * 0.55f +
                    cos(frac * 53f - globalTimeSec * 27f) * 0.45f
                ) * gAmp * 1.1f
            val y = gMidY + smoothSine * morphT + jagged * erraticWeight
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (waveColor and 0x00FFFFFF) or 0x66000000
            setStyle(Paint.Style.STROKE)
            strokeWidth = 14f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter((12f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = waveColor
            setStyle(Paint.Style.STROKE)
            strokeWidth = 6.5f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(wavePath, glowPaint)
        canvas.drawPath(wavePath, strokePaint)
    }

    private fun drawAnimatedChecklist(
        canvas: Canvas,
        card: RectF,
        u: Float,
        scene: SceneScript,
        sceneElapsedSec: Float
    ) {
        val items = scene.checklistItems.ifEmpty {
            listOf("Iwasan ang impulse spend", "Magtabi bago gumastos", "I-invest ang sobra")
        }.take(4)

        val pad = 32f * u
        val title = scene.hudLabel.ifBlank { "ACTION CHECKLIST" }.uppercase()
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5FF.toInt()
            textSize = 26f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        canvas.drawText(title, card.left + pad, card.top + 48f * u, headerPaint)

        val rowStartY = card.top + 88f * u
        val rowHeight = ((card.bottom - 20f * u - rowStartY) / items.size.coerceAtLeast(1)).coerceAtMost(56f * u)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E676.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 3.5f * u
        }
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E676.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 5.5f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 29f * u
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        items.forEachIndexed { idx, label ->
            val appearAt = idx * 0.6f
            val slideT = ((sceneElapsedSec - appearAt) / 0.28f).coerceIn(0f, 1f)
            if (slideT <= 0f) return@forEachIndexed

            val slideEase = Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, slideT)
            val tx = -(1f - slideEase) * 140f * u
            val rowCy = rowStartY + idx * rowHeight + rowHeight * 0.32f

            canvas.save()
            canvas.translate(tx, 0f)
            val boxSize = 30f * u
            val bx = card.left + pad
            val boxRect = RectF(bx, rowCy - boxSize * 0.5f, bx + boxSize, rowCy + boxSize * 0.5f)
            canvas.drawRoundRect(boxRect, 7f * u, 7f * u, boxPaint)

            // Animated checkmark via PathMeasure
            val checkT = ((sceneElapsedSec - (appearAt + 0.14f)) / 0.25f).coerceIn(0f, 1f)
            if (checkT > 0f) {
                val checkPath = Path().apply {
                    moveTo(bx + 6f * u, rowCy)
                    lineTo(bx + 13f * u, rowCy + 8f * u)
                    lineTo(bx + 25f * u, rowCy - 8f * u)
                }
                val pm = PathMeasure(checkPath, false)
                val partial = Path()
                pm.getSegment(0f, pm.length * checkT, partial, true)
                canvas.drawPath(partial, checkPaint)
            }

            val fm = textPaint.fontMetrics
            val textY = rowCy - (fm.ascent + fm.descent) * 0.5f
            val clipped = if (label.length > 34) label.take(32) + "…" else label
            canvas.drawText(clipped, bx + boxSize + 18f * u, textY, textPaint)
            canvas.restore()
        }
    }
}
