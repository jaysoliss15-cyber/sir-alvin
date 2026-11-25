package com.example.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.SceneScript
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders Kurzgesagt-style 2D cutaway engineering layers:
 * soil strata, house cutaway rooms/roof, ventilation fan, pipes, circuits,
 * and animated PathMeasure glowing airflow, light-beam & energy particles.
 */
object CrossSectionDiagramRenderer {

    fun drawCutaway(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneScript,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        hasActiveHud: Boolean
    ) {
        val u = w / 1080f
        val groundY = if (hasActiveHud) h * 0.485f else h * 0.56f

        // 1. Clean Kurzgesagt twilight-blue/teal engineering sky + warm solar beam cone
        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, groundY,
                intArrayOf(0xFF0B1C36.toInt(), 0xFF15365C.toInt(), 0xFF285F82.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, groundY, skyPaint)

        // Sun & animated light-beam cone toward roof solar array
        val sunX = w * 0.80f
        val sunY = h * 0.145f
        val sunGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sunX, sunY, 150f * u,
                intArrayOf(0xAAFFF099.toInt(), 0x33FFD166, 0x00FFD166),
                floatArrayOf(0.15f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sunX, sunY, 150f * u, sunGlow)
        canvas.drawCircle(sunX, sunY, 44f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE680.toInt() })

        // Volumetric sun-beam ribbon hitting the roof solar panel
        val roofTargetX = w * 0.63f
        val roofTargetY = groundY - 295f * u
        val beamAlpha = (42 + (sin(globalTimeSec * 3.5f) * 16f).toInt()).coerceIn(20, 75)
        val beamPath = Path().apply {
            moveTo(sunX - 18f * u, sunY + 12f * u)
            lineTo(sunX + 26f * u, sunY + 18f * u)
            lineTo(roofTargetX + 54f * u, roofTargetY + 12f * u)
            lineTo(roofTargetX - 48f * u, roofTargetY - 6f * u)
            close()
        }
        canvas.drawPath(beamPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(beamAlpha, 255, 224, 102)
        })

        // 2. Layered Soil & Bedrock Cross-Section Strata below groundY
        val topsoilH = 44f * u
        val clayH = 120f * u
        canvas.drawRect(0f, groundY, w, groundY + topsoilH, Paint().apply { color = 0xFF39A96B.toInt() })
        canvas.drawRect(0f, groundY + topsoilH, w, groundY + topsoilH + clayH, Paint().apply { color = 0xFF6F4834.toInt() })
        canvas.drawRect(0f, groundY + topsoilH + clayH, w, h, Paint().apply {
            shader = LinearGradient(
                0f, groundY + topsoilH + clayH, 0f, h,
                0xFF282436.toInt(), 0xFF10131F.toInt(), Shader.TileMode.CLAMP
            )
        })

        // Stylized pebbles inside soil layers
        val pebblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x441B1410 }
        for (i in 0 until 12) {
            val px = ((i * 187 + 70) % 980 + 50) * u
            val py = groundY + topsoilH + 22f * u + (i % 3) * 34f * u
            canvas.drawOval(RectF(px - 18f * u, py - 8f * u, px + 18f * u, py + 8f * u), pebblePaint)
        }

        // 3. House / System Cutaway Architecture
        val houseL = w * 0.14f
        val houseR = w * 0.86f
        val houseTop = groundY - 280f * u
        val midFloorY = groundY - 135f * u

        // Outer foundation & structural concrete wall frame
        val frameRect = RectF(houseL, houseTop, houseR, groundY + 12f * u)
        canvas.drawRoundRect(frameRect, 12f * u, 12f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E293B.toInt()
        })

        // Interior room cavities (Upper attic/studio + Lower living/utility)
        val upperRoom = RectF(houseL + 18f * u, houseTop + 18f * u, houseR - 18f * u, midFloorY - 8f * u)
        val lowerLeftRoom = RectF(houseL + 18f * u, midFloorY + 10f * u, w * 0.49f, groundY - 8f * u)
        val lowerRightRoom = RectF(w * 0.51f, midFloorY + 10f * u, houseR - 18f * u, groundY - 8f * u)

        canvas.drawRoundRect(upperRoom, 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF253754.toInt() })
        canvas.drawRoundRect(lowerLeftRoom, 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B3F5E.toInt() })
        canvas.drawRoundRect(lowerRightRoom, 8f * u, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF20324B.toInt() })

        // Pitched cutaway roof with attic truss & solar panel strip
        val peakX = w * 0.50f
        val peakY = houseTop - 135f * u
        val roofOuter = Path().apply {
            moveTo(peakX, peakY)
            lineTo(houseL - 32f * u, houseTop + 8f * u)
            lineTo(houseR + 32f * u, houseTop + 8f * u)
            close()
        }
        canvas.drawPath(roofOuter, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0533C.toInt() })

        val atticCavity = Path().apply {
            moveTo(peakX, peakY + 34f * u)
            lineTo(houseL + 34f * u, houseTop - 4f * u)
            lineTo(houseR - 34f * u, houseTop - 4f * u)
            close()
        }
        canvas.drawPath(atticCavity, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A2639.toInt() })

        // Roof Solar Array on right slope
        val solarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0EA5E9.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 14f * u
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(peakX + 26f * u, peakY + 10f * u, houseR - 20f * u, houseTop - 18f * u, solarPaint)

        // Spinning Ventilation Fan in upper right room
        drawSpinningFan(canvas, houseR - 72f * u, (upperRoom.top + upperRoom.bottom) * 0.5f, 34f * u, globalTimeSec)

        // Glowing Circuit / Battery Module in lower right room
        val battRect = RectF(lowerRightRoom.right - 96f * u, lowerRightRoom.top + 26f * u, lowerRightRoom.right - 30f * u, lowerRightRoom.bottom - 18f * u)
        canvas.drawRoundRect(battRect, 10f * u, 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })
        val chargeFrac = (0.35f + 0.65f * ((sceneElapsedSec / sceneDurationSec.coerceAtLeast(1f)).coerceIn(0f, 1f)))
        val chargeFillTop = battRect.bottom - 8f * u - (battRect.height() - 16f * u) * chargeFrac
        canvas.drawRoundRect(
            RectF(battRect.left + 8f * u, chargeFillTop, battRect.right - 8f * u, battRect.bottom - 8f * u),
            6f * u, 6f * u,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E676.toInt() }
        )

        // 4. Cutaway Utility Pipes (Underground intake + roof-to-room conduit)
        val pipeCasing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 22f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pipeCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F172A.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 12f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Underground water/utility conduit
        val groundPipe = Path().apply {
            moveTo(w * 0.04f, groundY + topsoilH + 58f * u)
            lineTo(w * 0.30f, groundY + topsoilH + 58f * u)
            lineTo(w * 0.30f, lowerLeftRoom.bottom - 24f * u)
            lineTo(w * 0.42f, lowerLeftRoom.bottom - 24f * u)
        }
        canvas.drawPath(groundPipe, pipeCasing)
        canvas.drawPath(groundPipe, pipeCore)

        // Solar / Power Circuit Pipe from roof -> attic -> battery
        val powerPipe = Path().apply {
            moveTo(roofTargetX, roofTargetY + 12f * u)
            lineTo(peakX + 80f * u, houseTop - 16f * u)
            lineTo(battRect.centerX(), upperRoom.top + 38f * u)
            lineTo(battRect.centerX(), battRect.top)
        }
        canvas.drawPath(powerPipe, pipeCasing)
        canvas.drawPath(powerPipe, pipeCore)

        // Airflow ventilation duct across upper ceiling out through spinning fan
        val airflowPath = Path().apply {
            moveTo(lowerLeftRoom.left + 32f * u, lowerLeftRoom.centerY())
            quadTo(w * 0.32f, midFloorY - 24f * u, w * 0.56f, upperRoom.centerY())
            lineTo(houseR - 72f * u, upperRoom.centerY())
        }

        // 5. PathMeasure Glowing Flow Ribbons & Traveling Energy Particles
        val revealProgress = Easing.smoothstep((sceneElapsedSec / 0.9f).coerceIn(0f, 1f))
        drawAnimatedFlowPath(
            canvas = canvas,
            path = groundPipe,
            glowColor = 0xFF38BDF8.toInt(),
            coreColor = 0xFFE0F2FE.toInt(),
            revealFrac = revealProgress,
            globalTimeSec = globalTimeSec,
            speed = 0.55f,
            u = u,
            particleCount = 4
        )
        drawAnimatedFlowPath(
            canvas = canvas,
            path = powerPipe,
            glowColor = 0xFFFFD166.toInt(),
            coreColor = 0xFFFFF8DC.toInt(),
            revealFrac = revealProgress,
            globalTimeSec = globalTimeSec,
            speed = 0.72f,
            u = u,
            particleCount = 4
        )
        drawAnimatedFlowPath(
            canvas = canvas,
            path = airflowPath,
            glowColor = 0xFF2DD4BF.toInt(),
            coreColor = 0xFFCCFBF1.toInt(),
            revealFrac = revealProgress,
            globalTimeSec = globalTimeSec,
            speed = 0.65f,
            u = u,
            particleCount = 5
        )
    }

    private fun drawSpinningFan(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        globalTimeSec: Float
    ) {
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF64748B.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = radius * 0.14f
        })
        val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() }
        val angleDeg = (globalTimeSec * 260f) % 360f
        canvas.save()
        canvas.rotate(angleDeg, cx, cy)
        for (i in 0 until 4) {
            canvas.rotate(90f, cx, cy)
            canvas.drawOval(
                RectF(cx - radius * 0.22f, cy - radius * 0.78f, cx + radius * 0.22f, cy - radius * 0.12f),
                bladePaint
            )
        }
        canvas.restore()
        canvas.drawCircle(cx, cy, radius * 0.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private fun drawAnimatedFlowPath(
        canvas: Canvas,
        path: Path,
        glowColor: Int,
        coreColor: Int,
        revealFrac: Float,
        globalTimeSec: Float,
        speed: Float,
        u: Float,
        particleCount: Int
    ) {
        val pm = PathMeasure(path, false)
        val totalLen = pm.length
        if (totalLen <= 1f) return

        val partial = Path()
        pm.getSegment(0f, totalLen * revealFrac.coerceIn(0f, 1f), partial, true)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (glowColor and 0x00FFFFFF) or 0x77000000
            setStyle(Paint.Style.STROKE)
            strokeWidth = 14f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter((10f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glowColor
            setStyle(Paint.Style.STROKE)
            strokeWidth = 5f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(partial, glowPaint)
        canvas.drawPath(partial, linePaint)

        // Glowing particles gliding smoothly along PathMeasure
        val pos = FloatArray(2)
        val particleGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (glowColor and 0x00FFFFFF) or 0xAA000000.toInt()
            maskFilter = BlurMaskFilter((8f * u).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        val particleCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coreColor }

        for (i in 0 until particleCount) {
            val t = ((globalTimeSec * speed + i.toFloat() / particleCount) % 1f)
            if (t > revealFrac) continue
            pm.getPosTan(totalLen * t, pos, null)
            canvas.drawCircle(pos[0], pos[1], 11f * u, particleGlow)
            canvas.drawCircle(pos[0], pos[1], 5.5f * u, particleCore)
        }
    }
}
