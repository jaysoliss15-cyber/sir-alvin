package com.example.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

object Easing {

    fun clamp01(t: Float): Float = t.coerceIn(0f, 1f)

    fun smoothstep(t: Float): Float {
        val x = clamp01(t)
        return x * x * (3f - 2f * x)
    }

    /**
     * Evaluates a 1D cubic Bezier curve with control points (x1, y1), (x2, y2).
     */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, tInput: Float): Float {
        val t = clamp01(tInput)
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f

        var u = t
        // Newton-Raphson iterations to solve x(u) = t
        for (i in 0 until 5) {
            val inv = 1f - u
            val x = 3f * inv * inv * u * x1 + 3f * inv * u * u * x2 + u * u * u
            val dx = 3f * inv * inv * x1 + 6f * inv * u * (x2 - x1) + 3f * u * u * (1f - x2)
            if (kotlin.math.abs(dx) < 1e-5f) break
            u = (u - (x - t) / dx).coerceIn(0f, 1f)
        }
        val inv = 1f - u
        return 3f * inv * inv * u * y1 + 3f * inv * u * u * y2 + u * u * u
    }

    fun easeOutBack(tInput: Float): Float {
        val t = clamp01(tInput)
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val tm1 = t - 1f
        return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1
    }

    fun springBounce(tInput: Float): Float {
        val t = clamp01(tInput)
        return (1f - exp(-5f * t) * cos(10f * PI.toFloat() * t)).coerceIn(0f, 1.25f)
    }

    fun pingPongWave(timeSec: Float, freqHz: Float): Float {
        return 0.5f + 0.5f * sin(timeSec * freqHz * 2f * PI.toFloat())
    }
}
