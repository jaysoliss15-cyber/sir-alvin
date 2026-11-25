package com.example.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import com.example.art.ProceduralBackdropGenerator
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import com.example.model.CharacterPose
import com.example.model.MetaphorVisual
import com.example.model.ProductionMode
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
        fallbackBackdropCache: MutableMap<Int, Bitmap>? = null,
        productionMode: ProductionMode = ProductionMode.FULL_AI_STORY,
        customSprites: Map<ActorRole, Bitmap> = emptyMap(),
        externalClipFrameProvider: ((sceneIndex: Int, localTimeMs: Double) -> Bitmap?)? = null
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
        val sceneDurationSec = (active.durationMs / 1000.0).toFloat().coerceAtLeast(1f)
        val sceneProgress = (sceneElapsedMs / active.durationMs).toFloat().coerceIn(0f, 1f)
        val globalTimeSec = (currentMs / 1000.0).toFloat()

        val hasActiveHud = ProceduralPuppetEngine.hasActiveHudOverlay(active.scene, style, productionMode)
        val shotFraming = ProceduralPuppetEngine.resolveShotFraming(active.scene)
        val resolvedPose = ProceduralPuppetEngine.resolveCharacterPose(active.scene)
        val resolvedProp = ProceduralPuppetEngine.resolveHandProp(active.scene, resolvedPose)
        val resolvedMetaphor = MetaphorVisualRenderer.resolveMetaphorVisual(active.scene, style)
        val resolvedExpression = ProceduralPuppetEngine.resolveExpression(active.scene, style)
        val isCutaway = style == AnimationStyle.CROSS_SECTION || productionMode == ProductionMode.CROSS_SECTION_EXPLAINER
        val isClipAutoEditor = productionMode == ProductionMode.CLIP_AUTO_EDITOR
        val hasDiagram = !isCutaway && !isClipAutoEditor && MetaphorVisualRenderer.isDiagramAlongsideCharacter(resolvedMetaphor)

        // 1. Cinematic 2D Shot Cuts & Camera Tracking via CameraController
        val cam = CameraController.computeCameraTransform(
            width = width,
            height = height,
            scene = active.scene,
            style = style,
            sceneIndex = activeIndex,
            sceneElapsedSec = sceneElapsedSec,
            sceneDurationSec = sceneDurationSec,
            globalTimeSec = globalTimeSec,
            hasActiveHud = hasActiveHud,
            isCutaway = isCutaway
        )

        canvas.drawColor(if (style == AnimationStyle.STICKMAN_WHITEBOARD) 0xFFFAF6EE.toInt() else Color.BLACK)

        canvas.save()
        canvas.translate(cam.translateX, cam.translateY)
        canvas.scale(cam.scale, cam.scale, cam.pivotX, cam.pivotY)

        // 2. Clip / Cutaway / Backdrop Layer
        val externalClipBitmap = if (isClipAutoEditor) {
            externalClipFrameProvider?.invoke(activeIndex, sceneElapsedMs)
        } else null

        if (isCutaway) {
            CrossSectionDiagramRenderer.drawCutaway(
                canvas = canvas,
                w = width,
                h = height,
                scene = active.scene,
                sceneElapsedSec = sceneElapsedSec,
                sceneDurationSec = sceneDurationSec,
                globalTimeSec = globalTimeSec,
                hasActiveHud = hasActiveHud
            )
        } else if (externalClipBitmap != null && !externalClipBitmap.isRecycled) {
            val src = Rect(0, 0, externalClipBitmap.width, externalClipBitmap.height)
            val dst = Rect(0, 0, width.toInt(), height.toInt())
            canvas.drawBitmap(externalClipBitmap, src, dst, bitmapPaint)

            // Top & bottom contrast scrims for crisp readability on raw user footage
            val topScrim = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height * 0.28f,
                    intArrayOf(0xAA000000.toInt(), 0x00000000),
                    null, Shader.TileMode.CLAMP
                )
            }
            val botScrim = Paint().apply {
                shader = LinearGradient(
                    0f, height * 0.56f, 0f, height,
                    intArrayOf(0x00000000, 0xCC060912.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width, height * 0.28f, topScrim)
            canvas.drawRect(0f, height * 0.56f, width, height, botScrim)
        } else if (resolvedMetaphor == MetaphorVisual.SUNBURST_SPOTLIGHT) {
            MetaphorVisualRenderer.drawSunburstBackground(
                canvas = canvas,
                w = width,
                h = height,
                scene = active.scene,
                globalTimeSec = globalTimeSec
            )
        } else {
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
        }

        // 3. Determine active speech & lip-sync pulse from sample-accurate WordTimings
        val activeWord = active.wordTimings.firstOrNull { currentMs >= it.startMs && currentMs <= it.endMs }
        val isSpeaking = activeWord != null
        val speechPulse = if (activeWord != null) {
            val span = (activeWord.endMs - activeWord.startMs).coerceAtLeast(40.0)
            val localFrac = ((currentMs - activeWord.startMs) / span).toFloat().coerceIn(0f, 1f)
            kotlin.math.abs(sin(localFrac * Math.PI.toFloat() * 2f))
        } else 0f

        // 4. World Physics / Prop FX Layer (inside camera space behind foreground HUD)
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

        // 4b. Animated 2D Visual Metaphor Diagram alongside character
        if (hasDiagram) {
            MetaphorVisualRenderer.drawDiagram(
                canvas = canvas,
                w = width,
                h = height,
                metaphor = resolvedMetaphor,
                scene = active.scene,
                sceneElapsedSec = sceneElapsedSec,
                sceneDurationSec = sceneDurationSec,
                globalTimeSec = globalTimeSec
            )
        }

        // 5. Articulated Multi-Pose Puppet Layer (re-applies camera transform inside screen-space HUD safe clip)
        if (!isClipAutoEditor) {
            canvas.save()
            if (hasActiveHud) {
                canvas.clipRect(0f, 0f, width, height * (1112f / 1920f))
            }
            canvas.translate(cam.translateX, cam.translateY)
            canvas.scale(cam.scale, cam.scale, cam.pivotX, cam.pivotY)
            val customSprite = customSprites[active.scene.actorRole]
            val effectivePose = if (hasDiagram && resolvedPose == CharacterPose.TWO_CHARACTER_CONVERSATION) {
                CharacterPose.POINT_AND_GESTURE
            } else {
                resolvedPose
            }
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
                speechPulse = speechPulse,
                hasActiveHud = hasActiveHud,
                shotFraming = shotFraming,
                customSprite = customSprite,
                scaleMultiplier = when {
                    isCutaway -> 0.64f
                    hasDiagram -> 0.78f
                    else -> 1f
                },
                extraOffsetX = when {
                    isCutaway -> -260f
                    hasDiagram -> -250f
                    else -> 0f
                },
                characterPose = effectivePose,
                handProp = resolvedProp,
                secondaryRole = active.scene.secondaryActorRole,
                sceneDurationSec = sceneDurationSec,
                sceneId = active.scene.sceneId,
                customSprites = customSprites,
                expression = resolvedExpression,
                headgear = active.scene.headgear
            )
            canvas.restore()
        }

        // 6. Directional Motion Blur Streak for WHIP_CUT transitions
        if (cam.whipBlurStrength > 0.04f) {
            CameraController.drawWhipMotionBlurStreaks(
                canvas = canvas,
                width = width,
                height = height,
                blurStrength = cam.whipBlurStrength,
                direction = cam.whipDirection
            )
        }

        // 7. Screen-Space Style Overlay
        WorldFxAndCaptionRenderer.drawStyleOverlay(
            canvas = canvas,
            w = width,
            h = height,
            style = style,
            globalTimeSec = globalTimeSec
        )

        // 7. Kinetic Motion-Graphics HUD Overlay (Lower-Third y=1120..1420px)
        MotionGraphicsOverlayRenderer.drawOverlays(
            canvas = canvas,
            w = width,
            h = height,
            scene = active.scene,
            style = style,
            sceneElapsedSec = sceneElapsedSec,
            sceneDurationSec = sceneDurationSec,
            globalTimeSec = globalTimeSec,
            productionMode = productionMode
        )

        // 8. Word-by-Word Kinetic Karaoke Captions (y=1520px)
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
