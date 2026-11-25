package com.example.render

import com.example.model.CharacterPose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class SpringFollowThrough(
    val headLagDeg: Float = 0f,
    val hairHatTiltDeg: Float = 0f,
    val trailingArmLagDeg: Float = 0f,
    val verticalBounceLagPx: Float = 0f
)

/**
 * Deterministic stateless damped harmonic spring integrator evaluated at 30fps sub-steps.
 * Produces ~3-4 frames (100-133ms) of authentic secondary motion lag & subtle overshoot on head,
 * hair/hat, and trailing arms whenever the torso accelerates, stops, or lands from a jump.
 */
object PuppetSpringSolver {

    private const val STEP_DT = 1f / 30f
    private const val LOOKBACK_STEPS = 14

    data class TorsoSample(
        val offsetX: Float,
        val offsetY: Float,
        val bodyTiltDeg: Float
    )

    fun sampleWalkAcrossProgress(sceneElapsedSec: Float, sceneDurationSec: Float): Float {
        val walkWindow = minOf((sceneDurationSec * 0.86f).coerceAtLeast(1.8f), 3.6f)
        val raw = (sceneElapsedSec / walkWindow).coerceIn(0f, 1f)
        return Easing.cubicBezier(0.25f, 0.1f, 0.25f, 1f, raw)
    }

    fun computeWalkOffsetX(sceneElapsedSec: Float, sceneDurationSec: Float): Float {
        val p = sampleWalkAcrossProgress(sceneElapsedSec, sceneDurationSec)
        return -320f + 560f * p
    }

    fun sampleTorsoAt(
        pose: CharacterPose,
        tSec: Float,
        sceneDurationSec: Float
    ): TorsoSample {
        val t = tSec.coerceAtLeast(0f)
        return when (pose) {
            CharacterPose.WALK_ACROSS_LEFT_TO_RIGHT -> {
                val p = sampleWalkAcrossProgress(t, sceneDurationSec)
                val isMoving = p < 0.992f
                val stridePhase = t * 10.5f
                val bob = if (isMoving) -abs(sin(stridePhase)) * 16f else 0f
                val lean = if (isMoving) 6.5f + sin(stridePhase) * 2.5f else 0f
                TorsoSample(
                    offsetX = computeWalkOffsetX(t, sceneDurationSec),
                    offsetY = bob,
                    bodyTiltDeg = lean
                )
            }
            CharacterPose.SHOCK_AIRBORNE_RECOIL -> {
                val arcT = (t / 0.88f).coerceIn(0f, 1f)
                val air = sin(arcT * PI.toFloat())
                val recoilY = -air * 195f
                val lean = if (arcT < 0.95f) -11f * air else 4f * sin((t - 0.88f) * 14f).coerceIn(-1f, 1f)
                TorsoSample(offsetX = -18f * air, offsetY = recoilY, bodyTiltDeg = lean)
            }
            CharacterPose.CELEBRATE_JUMP -> {
                val period = 0.95f
                val local = (t % period) / period
                val arc = sin(local * PI.toFloat())
                val airY = -arc * 155f
                TorsoSample(
                    offsetX = 0f,
                    offsetY = airY,
                    bodyTiltDeg = sin(t * 6.5f) * 4f
                )
            }
            CharacterPose.SIT_SLUMPED -> {
                val settle = Easing.cubicBezier(0.22f, 1f, 0.36f, 1f, (t / 0.45f).coerceIn(0f, 1f))
                TorsoSample(
                    offsetX = 0f,
                    offsetY = 64f * settle + sin(t * 2.2f) * 3.5f,
                    bodyTiltDeg = 17f * settle + sin(t * 2.2f) * 1.4f
                )
            }
            CharacterPose.POINT_AND_GESTURE -> {
                val pop = Easing.easeOutBack((t / 0.4f).coerceIn(0f, 1f))
                TorsoSample(
                    offsetX = 14f * pop,
                    offsetY = -abs(sin(t * 5.2f)) * 5f,
                    bodyTiltDeg = -4.5f * pop + sin(t * 4.2f) * 2f
                )
            }
            CharacterPose.TWO_CHARACTER_CONVERSATION -> {
                TorsoSample(
                    offsetX = 0f,
                    offsetY = -abs(sin(t * 4.2f)) * 4f,
                    bodyTiltDeg = 4.5f + sin(t * 3.6f) * 2.0f
                )
            }
            CharacterPose.THREE_QUARTER_EXPLAIN, CharacterPose.AUTO -> {
                val enter = Easing.cubicBezier(0.16f, 1f, 0.3f, 1f, (t / 0.5f).coerceIn(0f, 1f))
                TorsoSample(
                    offsetX = -32f * (1f - enter),
                    offsetY = -abs(sin(t * 4.6f)) * 5f,
                    bodyTiltDeg = sin(t * 3.4f) * 2.5f
                )
            }
        }
    }

    fun solve(
        pose: CharacterPose,
        sceneElapsedSec: Float,
        sceneDurationSec: Float
    ): SpringFollowThrough {
        val startT = (sceneElapsedSec - LOOKBACK_STEPS * STEP_DT).coerceAtLeast(0f)
        val initial = sampleTorsoAt(pose, startT, sceneDurationSec)

        // Head spring (omega ~ 17.5 rad/s, zeta ~ 0.44 -> ~3 frame lag)
        var headX = initial.bodyTiltDeg * 0.65f
        var headV = 0f
        // Hair/Hat spring (omega ~ 14.5 rad/s, zeta ~ 0.36 -> ~4 frame lag + lively bounce)
        var hairX = initial.bodyTiltDeg * 0.9f
        var hairV = 0f
        // Trailing arm spring (omega ~ 16.0 rad/s, zeta ~ 0.40 -> ~3-4 frame lag)
        var armX = 0f
        var armV = 0f
        // Vertical landing bounce spring (omega ~ 18.0 rad/s, zeta ~ 0.42)
        var vertX = 0f
        var vertV = 0f

        var prevSample = sampleTorsoAt(pose, (startT - STEP_DT).coerceAtLeast(0f), sceneDurationSec)

        for (i in 0..LOOKBACK_STEPS) {
            val t = (startT + i * STEP_DT).coerceAtMost(sceneElapsedSec)
            val cur = sampleTorsoAt(pose, t, sceneDurationSec)
            val vx = (cur.offsetX - prevSample.offsetX) / STEP_DT
            val vy = (cur.offsetY - prevSample.offsetY) / STEP_DT
            prevSample = cur

            val headTarget = (cur.bodyTiltDeg * 0.72f - vx * 0.018f + vy * 0.014f).coerceIn(-18f, 18f)
            val hairTarget = (cur.bodyTiltDeg * 1.15f - vx * 0.030f + vy * 0.022f).coerceIn(-24f, 24f)
            val armTarget = (-vx * 0.042f + vy * 0.026f - cur.bodyTiltDeg * 0.8f).coerceIn(-28f, 28f)
            val vertTarget = (vy * 0.028f).coerceIn(-20f, 20f)

            // Semi-implicit Euler integration for crisp stability at 30Hz
            val headA = 306f * (headTarget - headX) - 15.4f * headV
            headV += headA * STEP_DT
            headX += headV * STEP_DT

            val hairA = 210f * (hairTarget - hairX) - 10.4f * hairV
            hairV += hairA * STEP_DT
            hairX += hairV * STEP_DT

            val armA = 256f * (armTarget - armX) - 12.8f * armV
            armV += armA * STEP_DT
            armX += armV * STEP_DT

            val vertA = 324f * (vertTarget - vertX) - 15.1f * vertV
            vertV += vertA * STEP_DT
            vertX += vertV * STEP_DT
        }

        return SpringFollowThrough(
            headLagDeg = headX.coerceIn(-18f, 18f),
            hairHatTiltDeg = hairX.coerceIn(-24f, 24f),
            trailingArmLagDeg = armX.coerceIn(-28f, 28f),
            verticalBounceLagPx = vertX.coerceIn(-18f, 18f)
        )
    }
}
