package com.example.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.example.model.AnimationStyle
import com.example.model.CameraMotion
import com.example.model.CharacterPose
import com.example.model.SceneScript
import com.example.model.ShotFraming
import com.example.model.ShotType
import kotlin.math.sin

data class CameraTransform(
    val scale: Float,
    val translateX: Float,
    val translateY: Float,
    val pivotX: Float,
    val pivotY: Float,
    val whipBlurStrength: Float = 0f,
    val whipDirection: Float = 1f,
    val resolvedShotType: ShotType = ShotType.WIDE_STAGE
)

object CameraController {

    fun resolveShotType(scene: SceneScript, resolvedPose: CharacterPose): ShotType {
        if (scene.shotType != ShotType.AUTO) return scene.shotType
        if (scene.cameraMotion == CameraMotion.WHIP_PAN_LEFT || scene.cameraMotion == CameraMotion.WHIP_PAN_RIGHT) {
            return ShotType.WHIP_CUT
        }
        return when (resolvedPose) {
            CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT -> ShotType.TRACKING_FOLLOW
            CharacterPose.SHOCK_AIRBORNE_RECOIL -> ShotType.CLOSE_UP_REACTION
            CharacterPose.TWO_CHARACTER_CONVERSATION -> ShotType.WIDE_STAGE
            else -> when (ProceduralPuppetEngine.resolveShotFraming(scene)) {
                ShotFraming.CLOSE_UP_FACE -> ShotType.CLOSE_UP_REACTION
                ShotFraming.MEDIUM_WAIST_UP -> ShotType.MEDIUM_WAIST_UP
                ShotFraming.WIDE_FULL_BODY -> ShotType.WIDE_STAGE
                ShotFraming.RULE_OF_THIRDS_LEFT,
                ShotFraming.RULE_OF_THIRDS_RIGHT -> ShotType.MEDIUM_WAIST_UP
                ShotFraming.AUTO -> ShotType.WIDE_STAGE
            }
        }
    }

    fun computeCameraTransform(
        width: Float,
        height: Float,
        scene: SceneScript,
        style: AnimationStyle,
        sceneIndex: Int,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float,
        hasActiveHud: Boolean,
        isCutaway: Boolean
    ): CameraTransform {
        val resolvedPose = ProceduralPuppetEngine.resolveCharacterPose(scene)
        val shotFraming = ProceduralPuppetEngine.resolveShotFraming(scene)
        val shotType = resolveShotType(scene, resolvedPose)

        val sceneProgress = (sceneElapsedSec / sceneDurationSec.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val framingEase = Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, (sceneElapsedSec / 0.62f).coerceIn(0f, 1f))
        val scaleUnit = width / 1080f

        var camScale = when {
            isCutaway -> 1.0f + 0.04f * sceneProgress
            shotType == ShotType.CLOSE_UP_REACTION -> 1.0f + 0.90f * framingEase   // settles at 1.90x
            shotType == ShotType.MEDIUM_WAIST_UP -> 1.0f + 0.45f * framingEase     // settles at 1.45x
            shotType == ShotType.TRACKING_FOLLOW -> 1.0f + 0.24f * framingEase     // crisp tracking frame
            shotType == ShotType.WHIP_CUT -> 1.08f
            else -> 1.0f
        }

        var camTx = when {
            isCutaway -> 0f
            shotFraming == ShotFraming.RULE_OF_THIRDS_LEFT -> width * 0.045f * framingEase
            shotFraming == ShotFraming.RULE_OF_THIRDS_RIGHT -> -width * 0.045f * framingEase
            else -> 0f
        }
        var camTy = 0f

        // Smooth tracking follow pan synced to walking character's horizontal translation
        if (!isCutaway && (shotType == ShotType.TRACKING_FOLLOW || resolvedPose == CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT)) {
            val charOffsetX = PuppetSpringSolver.computeWalkOffsetX(sceneElapsedSec, sceneDurationSec)
            val hudScale = if (hasActiveHud) 0.82f else 1.0f
            camTx += -charOffsetX * scaleUnit * hudScale * 0.52f
        }

        val zoomPivotX = width * 0.5f
        val zoomPivotY = when {
            (shotType == ShotType.CLOSE_UP_REACTION || shotType == ShotType.MEDIUM_WAIST_UP) && hasActiveHud ->
                height * 0.50f
            shotType == ShotType.CLOSE_UP_REACTION || shotType == ShotType.MEDIUM_WAIST_UP ->
                height * 0.55f
            else -> height * 0.5f
        }

        var whipBlurStrength = 0f
        var whipDir = if (scene.cameraMotion == CameraMotion.WHIP_PAN_RIGHT) -1f else 1f

        if (shotType == ShotType.WHIP_CUT) {
            val whipProgress = (sceneElapsedSec / 0.36f).coerceIn(0f, 1f)
            val whipRemain = 1f - Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, whipProgress)
            if (scene.cameraMotion != CameraMotion.WHIP_PAN_RIGHT && scene.cameraMotion != CameraMotion.WHIP_PAN_LEFT) {
                whipDir = if (sceneIndex % 2 == 0) 1f else -1f
            }
            camTx += whipDir * width * 0.52f * whipRemain
            camScale = maxOf(camScale, 1.06f)
            whipBlurStrength = (1f - whipProgress).coerceIn(0f, 1f)
        }

        when (scene.cameraMotion) {
            CameraMotion.WHIP_PAN_LEFT -> {
                if (shotType != ShotType.WHIP_CUT) {
                    val whipProgress = (sceneElapsedSec / 0.40f).coerceIn(0f, 1f)
                    val whip = 1f - Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, whipProgress)
                    camTx += width * 0.45f * whip
                    camScale = maxOf(camScale, 1.04f)
                    whipBlurStrength = maxOf(whipBlurStrength, (1f - whipProgress) * 0.9f)
                    whipDir = 1f
                }
            }
            CameraMotion.WHIP_PAN_RIGHT -> {
                if (shotType != ShotType.WHIP_CUT) {
                    val whipProgress = (sceneElapsedSec / 0.40f).coerceIn(0f, 1f)
                    val whip = 1f - Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, whipProgress)
                    camTx -= width * 0.45f * whip
                    camScale = maxOf(camScale, 1.04f)
                    whipBlurStrength = maxOf(whipBlurStrength, (1f - whipProgress) * 0.9f)
                    whipDir = -1f
                }
            }
            CameraMotion.ZOOM_IN -> {
                val z = Easing.cubicBezier(0.25f, 0.1f, 0.25f, 1f, sceneProgress)
                camScale += 0.10f * z
            }
            CameraMotion.ZOOM_OUT -> {
                val z = Easing.cubicBezier(0.25f, 0.1f, 0.25f, 1f, sceneProgress)
                camScale = (camScale + 0.12f * (1f - z)).coerceAtLeast(1.0f)
            }
            CameraMotion.SHAKE -> {
                camScale = maxOf(camScale, 1.05f)
                camTx += sin(globalTimeSec * 48f) * (width * 0.012f)
                camTy += sin(globalTimeSec * 39f + 1.1f) * (height * 0.008f)
            }
            CameraMotion.STATIC -> Unit
        }

        // Style-specific extra camera flavor
        if (style == AnimationStyle.DARK_SUSPENSE && scene.cameraMotion == CameraMotion.STATIC) {
            camScale += 0.06f * sceneProgress
            camTx += sin(globalTimeSec * 29f) * (width * 0.003f)
        } else if (style == AnimationStyle.TECH_EXPLAINER && scene.cameraMotion == CameraMotion.STATIC) {
            val z = Easing.cubicBezier(0.25f, 0.1f, 0.25f, 1f, sceneProgress)
            camScale += 0.05f * z
        } else if (style == AnimationStyle.PAPER_DIORAMA && sceneElapsedSec < 0.28f && sceneIndex > 0 && shotType != ShotType.WHIP_CUT) {
            val t = 1f - Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, sceneElapsedSec / 0.28f)
            camTx += width * 0.18f * t
        }

        return CameraTransform(
            scale = camScale,
            translateX = camTx,
            translateY = camTy,
            pivotX = zoomPivotX,
            pivotY = zoomPivotY,
            whipBlurStrength = whipBlurStrength,
            whipDirection = whipDir,
            resolvedShotType = shotType
        )
    }

    /**
     * Renders directional horizontal speed-blur streaks during WHIP_CUT transitions.
     */
    fun drawWhipMotionBlurStreaks(
        canvas: Canvas,
        width: Float,
        height: Float,
        blurStrength: Float,
        direction: Float
    ) {
        if (blurStrength <= 0.04f) return
        val u = width / 1080f
        val alpha = (blurStrength * 145f).toInt().coerceIn(0, 175)

        val washPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val cEdge = Color.argb((alpha * 0.45f).toInt(), 255, 255, 255)
            val cMid = Color.argb(0, 255, 255, 255)
            shader = if (direction >= 0f) {
                LinearGradient(0f, 0f, width, 0f, intArrayOf(cEdge, cMid, cEdge), null, Shader.TileMode.CLAMP)
            } else {
                LinearGradient(width, 0f, 0f, 0f, intArrayOf(cEdge, cMid, cEdge), null, Shader.TileMode.CLAMP)
            }
        }
        canvas.drawRect(0f, 0f, width, height, washPaint)

        val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 14) {
            val y = height * (0.07f + i * 0.065f)
            val len = width * (0.35f + (i % 3) * 0.22f) * blurStrength
            val xStart = ((i * 217f) % width) - len * 0.3f
            val xEnd = xStart + len
            val streakAlpha = ((alpha * (0.45f + (i % 3) * 0.2f))).toInt().coerceIn(0, 210)
            streakPaint.strokeWidth = (4f + (i % 4) * 3.5f) * u
            streakPaint.shader = LinearGradient(
                xStart, y, xEnd, y,
                intArrayOf(0x00FFFFFF, Color.argb(streakAlpha, 240, 248, 255), 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawLine(xStart, y, xEnd, y, streakPaint)
        }
    }
}
