package com.example.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.art.ProceduralBackdropGenerator
import com.example.model.AnimationStyle
import com.example.model.CameraMotion
import com.example.model.TimelineScene
import kotlin.math.sin

object SceneCompositor {

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun drawFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        scenes: List<TimelineScene>,
        style: AnimationStyle,
        currentMs: Double,
        fallbackBackdropCache: MutableMap<Int, Bitmap>? = null
    ) {
        if (scenes.isEmpty()) {
            canvas.drawColor(Color.parseColor("#0D0F17"))
            return
        }

        val activeIndex = scenes.indexOfFirst { currentMs >= it.startMs && currentMs < it.endMs }
            .let { if (it == -1) (scenes.size - 1) else it }
        val active = scenes[activeIndex]

        val sceneElapsedMs = (currentMs - active.startMs).coerceAtLeast(0.0)
        val sceneElapsedSec = (sceneElapsedMs / 1000.0).toFloat()
        val sceneProgress = (sceneElapsedMs / active.durationMs).toFloat().coerceIn(0f, 1f)
        val globalTimeSec = (currentMs / 1000.0).toFloat()

        // 1. Camera Transform (Whip-pan, Zoom, Tension Shake, Static)
        var camScale = 1.0f
        var camTx = 0f
        var camTy = 0f

        when (active.scene.cameraMotion) {
            CameraMotion.WHIP_PAN_LEFT -> {
                val whip = 1f - Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, (sceneElapsedSec / 0.42f))
                camTx = width * 0.45f * whip
                camScale = 1.04f
            }
            CameraMotion.WHIP_PAN_RIGHT -> {
                val whip = 1f - Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, (sceneElapsedSec / 0.42f))
                camTx = -width * 0.45f * whip
                camScale = 1.04f
            }
            CameraMotion.ZOOM_IN -> {
                val z = Easing.cubicBezier(0.25f, 0.1f, 0.25f, 1f, sceneProgress)
                camScale = 1.0f + 0.14f * z
            }
            CameraMotion.ZOOM_OUT -> {
                val z = Easing.cubicBezier(0.25f, 0.1f, 0.25f, 1f, sceneProgress)
                camScale = 1.14f - 0.14f * z
            }
            CameraMotion.SHAKE -> {
                camScale = 1.05f
                camTx = sin(globalTimeSec * 48f) * (width * 0.012f)
                camTy = sin(globalTimeSec * 39f + 1.1f) * (height * 0.008f)
            }
            CameraMotion.STATIC -> {
                camScale = 1.0f
            }
        }

        // Style-specific extra camera flavor
        if (style == AnimationStyle.DARK_SUSPENSE && active.scene.cameraMotion == CameraMotion.STATIC) {
            camScale = 1.0f + 0.08f * sceneProgress
            camTx += sin(globalTimeSec * 29f) * (width * 0.003f)
        } else if (style == AnimationStyle.PAPER_DIORAMA && sceneElapsedSec < 0.28f && activeIndex > 0) {
            // Fast whip-pan entry between paper diorama scenes
            val t = 1f - Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, sceneElapsedSec / 0.28f)
            camTx += width * 0.18f * t
        }

        canvas.drawColor(if (style == AnimationStyle.STICKMAN_WHITEBOARD) 0xFFFAF6EE.toInt() else Color.BLACK)

        canvas.save()
        canvas.translate(camTx, camTy)
        canvas.scale(camScale, camScale, width * 0.5f, height * 0.5f)

        // 2. Backdrop Layer
        val backdrop = active.backdropBitmap ?: fallbackBackdropCache?.getOrPut(active.scene.sceneId) {
            ProceduralBackdropGenerator.generate(
                prompt = active.scene.backgroundPrompt,
                style = style,
                width = width.toInt().coerceAtLeast(360),
                height = height.toInt().coerceAtLeast(640),
                seed = active.scene.sceneId * 997 + style.ordinal
            )
        }
        if (backdrop != null && !backdrop.isRecycled) {
            val src = Rect(0, 0, backdrop.width, backdrop.height)
            val dst = Rect(0, 0, width.toInt(), height.toInt())
            canvas.drawBitmap(backdrop, src, dst, bitmapPaint)
        }

        // 3. Determine active speech & lip-sync pulse from sample-accurate WordTimings
        val activeWord = active.wordTimings.firstOrNull { currentMs in it.startMs..it.endMs }
        val isSpeaking = activeWord != null
        val speechPulse = if (activeWord != null) {
            val span = (activeWord.endMs - activeWord.startMs).coerceAtLeast(40.0)
            val localFrac = ((currentMs - activeWord.startMs) / span).toFloat().coerceIn(0f, 1f)
            kotlin.math.abs(sin(localFrac * Math.PI.toFloat() * 2f))
        } else 0f

        // 4. Hierarchical Puppet Rig Layer
        ProceduralPuppetEngine.drawCharacter(
            canvas = canvas,
            stageW = width,
            stageH = height,
            role = active.scene.actorRole,
            action = active.scene.actorAction,
            style = style,
            sceneElapsedSec = sceneElapsedSec,
            globalTimeSec = globalTimeSec,
            isSpeaking = isSpeaking,
            speechPulse = speechPulse
        )

        // 5. World Physics / Prop FX Layer
        WorldFxAndCaptionRenderer.drawWorldFx(
            canvas = canvas,
            w = width,
            h = height,
            fx = active.scene.worldFx,
            style = style,
            sceneElapsedSec = sceneElapsedSec,
            globalTimeSec = globalTimeSec
        )

        canvas.restore()

        // 6. Screen-Space Style Overlay (Vignette + Film Grain / Vector Accents)
        WorldFxAndCaptionRenderer.drawStyleOverlay(
            canvas = canvas,
            w = width,
            h = height,
            style = style,
            globalTimeSec = globalTimeSec
        )

        // 7. Word-by-Word Karaoke Captions (always crisp & centered inside safe zone)
        WorldFxAndCaptionRenderer.drawKaraokeCaptions(
            canvas = canvas,
            w = width,
            h = height,
            style = style,
            wordTimings = active.wordTimings,
            fallbackNarration = active.scene.tagalogNarration,
            currentMs = currentMs
        )
    }
}
