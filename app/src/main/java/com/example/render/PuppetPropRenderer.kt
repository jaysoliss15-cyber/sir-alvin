package com.example.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.HandProp
import kotlin.math.cos
import kotlin.math.sin

object PuppetPropRenderer {

    enum class SeatType { CRATE, ROCK, STOOL }

    /**
     * Draws the chosen HandProp directly inside the active right-hand pivot matrix
     * (origin = hand center at the distal tip of the forearm), so the prop moves,
     * tilts, and gestures naturally with the articulated shoulder->elbow->hand chain.
     */
    fun drawHandProp(
        canvas: Canvas,
        handX: Float,
        handY: Float,
        u: Float,
        prop: HandProp,
        globalTimeSec: Float,
        silhouetteOnly: Boolean,
        outlinePaint: Paint,
        facingDir: Float = 1f
    ) {
        if (prop == HandProp.NONE) return

        canvas.save()
        canvas.translate(handX, handY)
        if (facingDir < 0f) {
            canvas.scale(-1f, 1f)
        }

        val strokeW = if (silhouetteOnly) 18f * u else 4f * u
        val propOutline = Paint(outlinePaint).apply { strokeWidth = strokeW }

        when (prop) {
            HandProp.NONE -> Unit

            HandProp.POINTER_STICK -> {
                if (silhouetteOnly) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        strokeWidth = 22f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(-10f * u, 4f * u, 155f * u, -48f * u, p)
                } else {
                    val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF8B5E3C.toInt()
                        strokeWidth = 10f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(-8f * u, 4f * u, 152f * u, -46f * u, shaftPaint)
                    val ferrulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFBBF24.toInt()
                        strokeWidth = 12f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(128f * u, -38f * u, 146f * u, -44f * u, ferrulePaint)
                    val tipGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFE066 }
                    canvas.drawCircle(154f * u, -47f * u, 18f * u, tipGlow)
                    canvas.drawCircle(154f * u, -47f * u, 9f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD166.toInt() })
                }
            }

            HandProp.MONEY_BAG -> {
                val bagRect = RectF(-42f * u, 6f * u, 44f * u, 106f * u)
                val neckPath = Path().apply {
                    moveTo(-18f * u, -10f * u)
                    lineTo(18f * u, -10f * u)
                    lineTo(10f * u, 12f * u)
                    lineTo(-10f * u, 12f * u)
                    close()
                }
                if (silhouetteOnly) {
                    canvas.drawOval(bagRect, propOutline)
                    canvas.drawPath(neckPath, propOutline)
                } else {
                    val sackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6C79C.toInt() }
                    val sackShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC59B6D.toInt() }
                    canvas.drawPath(neckPath, sackShadow)
                    canvas.drawOval(bagRect, sackPaint)
                    canvas.drawArc(bagRect, -20f, 130f, false, sackShadow)
                    // Rope tie
                    canvas.drawRoundRect(
                        RectF(-16f * u, 6f * u, 16f * u, 18f * u),
                        5f * u, 5f * u,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF92400E.toInt() }
                    )
                    if (strokeW > 0f) {
                        canvas.drawOval(bagRect, propOutline)
                    }
                    // Peso emblem
                    val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF15803D.toInt()
                        textSize = 44f * u
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    canvas.drawText("₱", 0f, 70f * u, emblemPaint)
                }
            }

            HandProp.PHONE_SCREEN -> {
                val phoneRect = RectF(-24f * u, -88f * u, 28f * u, 14f * u)
                val screenRect = RectF(-19f * u, -79f * u, 23f * u, 3f * u)
                if (silhouetteOnly) {
                    canvas.drawRoundRect(phoneRect, 9f * u, 9f * u, propOutline)
                } else {
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4438BDF8 }
                    canvas.drawRoundRect(
                        RectF(phoneRect.left - 8f * u, phoneRect.top - 8f * u, phoneRect.right + 8f * u, phoneRect.bottom + 8f * u),
                        14f * u, 14f * u, glowPaint
                    )
                    canvas.drawRoundRect(phoneRect, 9f * u, 9f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })
                    canvas.drawRoundRect(screenRect, 5f * u, 5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0284C7.toInt() })
                    // Animated notification bar & chart lines
                    val pulse = 0.7f + 0.3f * sin(globalTimeSec * 6f)
                    canvas.drawRoundRect(
                        RectF(-14f * u, -68f * u, 18f * u, -50f * u),
                        4f * u, 4f * u,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((220 * pulse).toInt(), 251, 191, 36) }
                    )
                    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBAE6FD.toInt() }
                    canvas.drawRoundRect(RectF(-14f * u, -40f * u, 12f * u, -33f * u), 3f * u, 3f * u, barPaint)
                    canvas.drawRoundRect(RectF(-14f * u, -26f * u, 18f * u, -19f * u), 3f * u, 3f * u, barPaint)
                    if (strokeW > 0f) canvas.drawRoundRect(phoneRect, 9f * u, 9f * u, propOutline)
                }
            }

            HandProp.MAGNIFYING_GLASS -> {
                val lensCx = 56f * u
                val lensCy = -54f * u
                val lensR = 34f * u
                if (silhouetteOnly) {
                    canvas.drawLine(0f, 0f, 34f * u, -32f * u, propOutline)
                    canvas.drawCircle(lensCx, lensCy, lensR, propOutline)
                } else {
                    val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF334155.toInt()
                        strokeWidth = 14f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(-4f * u, 4f * u, 34f * u, -32f * u, handlePaint)
                    val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x887DD3FC.toInt() }
                    canvas.drawCircle(lensCx, lensCy, lensR, glassPaint)
                    val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xCCFFFFFF.toInt()
                        setStyle(Paint.Style.STROKE)
                        strokeWidth = 5f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawArc(
                        RectF(lensCx - 22f * u, lensCy - 22f * u, lensCx + 22f * u, lensCy + 22f * u),
                        -145f, 65f, false, shinePaint
                    )
                    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFCBD5E1.toInt()
                        setStyle(Paint.Style.STROKE)
                        strokeWidth = 9f * u
                    }
                    canvas.drawCircle(lensCx, lensCy, lensR, rimPaint)
                    if (strokeW > 0f) {
                        canvas.drawCircle(lensCx, lensCy, lensR + 4f * u, propOutline)
                    }
                }
            }

            HandProp.LIGHTBULB -> {
                val bulbCx = 6f * u
                val bulbCy = -64f * u
                val bulbR = 30f * u
                if (silhouetteOnly) {
                    canvas.drawCircle(bulbCx, bulbCy, bulbR, propOutline)
                } else {
                    val pulse = 0.82f + 0.18f * sin(globalTimeSec * 8f)
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            bulbCx, bulbCy, 66f * u * pulse,
                            intArrayOf(0xAAFFE066.toInt(), 0x44F59E0B, 0x00F59E0B),
                            floatArrayOf(0f, 0.55f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawCircle(bulbCx, bulbCy, 66f * u * pulse, glowPaint)

                    // Animated idea rays
                    val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFE066.toInt()
                        strokeWidth = 4.5f * u
                        strokeCap = Paint.Cap.ROUND
                    }
                    for (i in 0 until 6) {
                        val a = Math.toRadians((-155.0 + i * 26.0))
                        val r0 = 38f * u
                        val r1 = (50f + 6f * pulse) * u
                        canvas.drawLine(
                            bulbCx + cos(a).toFloat() * r0,
                            bulbCy + sin(a).toFloat() * r0,
                            bulbCx + cos(a).toFloat() * r1,
                            bulbCy + sin(a).toFloat() * r1,
                            rayPaint
                        )
                    }

                    // Screw base
                    canvas.drawRoundRect(
                        RectF(bulbCx - 13f * u, bulbCy + 20f * u, bulbCx + 13f * u, bulbCy + 42f * u),
                        4f * u, 4f * u,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF94A3B8.toInt() }
                    )
                    // Bulb glass
                    canvas.drawCircle(bulbCx, bulbCy, bulbR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEF08A.toInt() })
                    if (strokeW > 0f) canvas.drawCircle(bulbCx, bulbCy, bulbR, propOutline)
                    // Filament
                    val filPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFF59E0B.toInt()
                        setStyle(Paint.Style.STROKE)
                        strokeWidth = 3.5f * u
                    }
                    canvas.drawLine(bulbCx - 8f * u, bulbCy + 16f * u, bulbCx - 4f * u, bulbCy - 4f * u, filPaint)
                    canvas.drawLine(bulbCx + 8f * u, bulbCy + 16f * u, bulbCx + 4f * u, bulbCy - 4f * u, filPaint)
                    canvas.drawLine(bulbCx - 4f * u, bulbCy - 4f * u, bulbCx + 4f * u, bulbCy - 4f * u, filPaint)
                }
            }
        }
        canvas.restore()
    }

    /**
     * Draws a grounded crate, rock, or wooden stool beneath the character for SIT_SLUMPED.
     */
    fun drawSeatProp(
        canvas: Canvas,
        rootX: Float,
        groundAnchorY: Float,
        u: Float,
        seatType: SeatType,
        silhouetteOnly: Boolean,
        outlinePaint: Paint
    ) {
        val seatTopY = groundAnchorY - 122f * u
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = 4f * u
        }

        when (seatType) {
            SeatType.CRATE -> {
                val rect = RectF(rootX - 96f * u, seatTopY, rootX + 96f * u, groundAnchorY + 8f * u)
                fillPaint.color = if (silhouetteOnly) Color.BLACK else 0xFF9A6B3F.toInt()
                canvas.drawRoundRect(rect, 10f * u, 10f * u, fillPaint)
                if (!silhouetteOnly) {
                    val inner = RectF(rect.left + 14f * u, rect.top + 14f * u, rect.right - 14f * u, rect.bottom - 14f * u)
                    fillPaint.color = 0xFF7C512B.toInt()
                    canvas.drawRect(inner, fillPaint)
                    detailPaint.color = 0xFF5C3A1D.toInt()
                    canvas.drawLine(inner.left, inner.top, inner.right, inner.bottom, detailPaint)
                    canvas.drawLine(inner.right, inner.top, inner.left, inner.bottom, detailPaint)
                    if (outlinePaint.strokeWidth > 0f) canvas.drawRoundRect(rect, 10f * u, 10f * u, outlinePaint)
                }
            }

            SeatType.ROCK -> {
                val rockPath = Path().apply {
                    moveTo(rootX - 110f * u, groundAnchorY + 8f * u)
                    quadTo(rootX - 114f * u, seatTopY + 12f * u, rootX - 58f * u, seatTopY)
                    quadTo(rootX, seatTopY - 10f * u, rootX + 66f * u, seatTopY + 4f * u)
                    quadTo(rootX + 114f * u, seatTopY + 28f * u, rootX + 106f * u, groundAnchorY + 8f * u)
                    close()
                }
                fillPaint.color = if (silhouetteOnly) Color.BLACK else 0xFF64748B.toInt()
                canvas.drawPath(rockPath, fillPaint)
                if (!silhouetteOnly) {
                    val highlight = Path().apply {
                        moveTo(rootX - 54f * u, seatTopY + 6f * u)
                        quadTo(rootX, seatTopY - 4f * u, rootX + 56f * u, seatTopY + 12f * u)
                        lineTo(rootX + 24f * u, seatTopY + 34f * u)
                        close()
                    }
                    canvas.drawPath(highlight, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF94A3B8.toInt() })
                    if (outlinePaint.strokeWidth > 0f) canvas.drawPath(rockPath, outlinePaint)
                }
            }

            SeatType.STOOL -> {
                val topRect = RectF(rootX - 88f * u, seatTopY, rootX + 88f * u, seatTopY + 24f * u)
                val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (silhouetteOnly) Color.BLACK else 0xFF6D4C41.toInt()
                    strokeWidth = 18f * u
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(rootX - 58f * u, seatTopY + 20f * u, rootX - 72f * u, groundAnchorY + 6f * u, legPaint)
                canvas.drawLine(rootX + 58f * u, seatTopY + 20f * u, rootX + 72f * u, groundAnchorY + 6f * u, legPaint)
                if (!silhouetteOnly) {
                    canvas.drawLine(rootX - 62f * u, seatTopY + 70f * u, rootX + 62f * u, seatTopY + 70f * u, legPaint)
                }
                fillPaint.color = if (silhouetteOnly) Color.BLACK else 0xFF8D6E63.toInt()
                canvas.drawRoundRect(topRect, 12f * u, 12f * u, fillPaint)
                if (!silhouetteOnly && outlinePaint.strokeWidth > 0f) {
                    canvas.drawRoundRect(topRect, 12f * u, 12f * u, outlinePaint)
                }
            }
        }
    }
}
