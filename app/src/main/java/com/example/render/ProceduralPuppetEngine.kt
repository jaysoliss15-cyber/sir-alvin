package com.example.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DiscretePathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.ActorAction
import com.example.model.ActorRole
import com.example.model.AnimationStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object ProceduralPuppetEngine {

    private data class RigPose(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val bodyTiltDeg: Float = 0f,
        val headTiltDeg: Float = 0f,
        val leftArmDeg: Float = 18f,
        val rightArmDeg: Float = -18f,
        val leftLegDeg: Float = 0f,
        val rightLegDeg: Float = 0f,
        val eyeScaleY: Float = 1f,
        val mouthOpen: Float = 0f,
        val eyebrowOffset: Float = 0f,
        val showThumbsUp: Boolean = false,
        val showPointerStick: Boolean = false,
        val tearDropLevel: Float = 0f,
        val emoteType: Int = 0 // 0=none, 1=sweatdrop, 2=anger/shock, 3=sparkles
    )

    private data class RolePalette(
        val skin: Int,
        val skinShadow: Int,
        val primary: Int,
        val primaryShadow: Int,
        val secondary: Int,
        val accent: Int,
        val hair: Int
    )

    fun drawCharacter(
        canvas: Canvas,
        stageW: Float,
        stageH: Float,
        role: ActorRole,
        action: ActorAction,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        isSpeaking: Boolean,
        speechPulse: Float
    ) {
        val pose = computeRigPose(
            action = action,
            style = style,
            sceneElapsedSec = sceneElapsedSec,
            globalTimeSec = globalTimeSec,
            isSpeaking = isSpeaking,
            speechPulse = speechPulse
        )

        val scaleUnit = stageW / 1080f
        val rootX = stageW * 0.5f + pose.offsetX * scaleUnit
        val rootY = stageH * 0.685f + pose.offsetY * scaleUnit

        // Ground drop shadow (strong in PAPER_DIORAMA and FLAT_VECTOR)
        if (style != AnimationStyle.STICKMAN_WHITEBOARD) {
            val shadowAlpha = when (style) {
                AnimationStyle.PAPER_DIORAMA -> 0x66
                AnimationStyle.DARK_SUSPENSE -> 0x88
                else -> 0x44
            }
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (shadowAlpha shl 24)
                maskFilter = BlurMaskFilter((18f * scaleUnit).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawOval(
                RectF(
                    rootX - 165f * scaleUnit,
                    stageH * 0.685f - 22f * scaleUnit,
                    rootX + 165f * scaleUnit,
                    stageH * 0.685f + 26f * scaleUnit
                ),
                shadowPaint
            )
        }

        if (style == AnimationStyle.STICKMAN_WHITEBOARD) {
            drawWhiteboardStickman(canvas, rootX, rootY, scaleUnit, role, pose, globalTimeSec)
            return
        }

        // Paper Diorama pass: thick 10px black cutout drop-silhouette behind the puppet
        if (style == AnimationStyle.PAPER_DIORAMA) {
            canvas.save()
            val cutM = Matrix().apply {
                postScale(pose.scaleX, pose.scaleY, rootX, rootY)
                postRotate(pose.bodyTiltDeg, rootX, rootY - 120f * scaleUnit)
            }
            canvas.concat(cutM)
            drawLayeredPuppet(
                canvas = canvas,
                rootX = rootX,
                rootY = rootY,
                u = scaleUnit,
                role = role,
                pose = pose,
                style = style,
                silhouetteOutlineOnly = true
            )
            canvas.restore()
        }

        // Main Character Hierarchical Pass
        canvas.save()
        val mainM = Matrix().apply {
            postScale(pose.scaleX, pose.scaleY, rootX, rootY)
            postRotate(pose.bodyTiltDeg, rootX, rootY - 120f * scaleUnit)
        }
        canvas.concat(mainM)
        drawLayeredPuppet(
            canvas = canvas,
            rootX = rootX,
            rootY = rootY,
            u = scaleUnit,
            role = role,
            pose = pose,
            style = style,
            silhouetteOutlineOnly = false
        )
        canvas.restore()

        // Anime emotes for CHIBI_STORYTIME or expressive reactions
        if (style == AnimationStyle.CHIBI_STORYTIME || pose.emoteType != 0) {
            drawAnimeEmotes(canvas, rootX, rootY - 470f * scaleUnit, scaleUnit, pose, globalTimeSec, style)
        }
    }

    private fun computeRigPose(
        action: ActorAction,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        globalTimeSec: Float,
        isSpeaking: Boolean,
        speechPulse: Float
    ): RigPose {
        // 3.5s natural blink cycle
        val blinkPhase = globalTimeSec % 3.5f
        val eyeScaleY = if (blinkPhase > 3.34f) {
            val b = (blinkPhase - 3.34f) / 0.16f
            abs(1f - 2f * b).coerceIn(0.08f, 1f)
        } else 1f

        val talkMouth = if (isSpeaking) {
            (0.28f + 0.72f * speechPulse).coerceIn(0.1f, 1f)
        } else {
            0.06f
        }

        val idleBounce = sin(globalTimeSec * 4.2f) * (if (style == AnimationStyle.PAPER_DIORAMA) 10f else 5f)
        val talkBounce = if (isSpeaking) abs(sin(globalTimeSec * 12f)) * 14f else 0f

        return when (action) {
            ActorAction.WALK_IN -> {
                val t = Easing.cubicBezier(0.22f, 1f, 0.36f, 1f, (sceneElapsedSec / 0.9f))
                val walkStride = if (sceneElapsedSec < 0.95f) sin(sceneElapsedSec * 16f) else 0f
                RigPose(
                    offsetX = -460f * (1f - t),
                    offsetY = -abs(walkStride) * 18f - talkBounce,
                    bodyTiltDeg = walkStride * 4f,
                    headTiltDeg = -walkStride * 3f,
                    leftArmDeg = 22f + walkStride * 38f,
                    rightArmDeg = -22f - walkStride * 38f,
                    leftLegDeg = -walkStride * 32f,
                    rightLegDeg = walkStride * 32f,
                    eyeScaleY = eyeScaleY,
                    mouthOpen = talkMouth
                )
            }
            ActorAction.TALK_BOUNCE -> {
                val squash = if (style == AnimationStyle.CHIBI_STORYTIME) {
                    0.06f * sin(globalTimeSec * 11f)
                } else 0.025f * sin(globalTimeSec * 9f)
                RigPose(
                    offsetY = -talkBounce - abs(idleBounce * 0.6f),
                    scaleX = 1f + squash,
                    scaleY = 1f - squash,
                    bodyTiltDeg = sin(globalTimeSec * 3.5f) * 2.5f,
                    headTiltDeg = sin(globalTimeSec * 5f) * 4f,
                    leftArmDeg = 28f + sin(globalTimeSec * 7f) * 20f,
                    rightArmDeg = -28f - sin(globalTimeSec * 7.5f) * 22f,
                    eyeScaleY = eyeScaleY,
                    mouthOpen = talkMouth,
                    emoteType = if (style == AnimationStyle.CHIBI_STORYTIME) 3 else 0
                )
            }
            ActorAction.THUMBS_UP -> {
                val pop = Easing.easeOutBack((sceneElapsedSec / 0.45f).coerceIn(0f, 1f))
                RigPose(
                    offsetY = -idleBounce * 0.5f - talkBounce * 0.5f,
                    scaleX = 0.94f + 0.06f * pop,
                    scaleY = 0.94f + 0.06f * pop,
                    headTiltDeg = -5f * pop,
                    leftArmDeg = 25f,
                    rightArmDeg = -115f * pop,
                    eyeScaleY = eyeScaleY,
                    mouthOpen = talkMouth,
                    eyebrowOffset = -6f,
                    showThumbsUp = true,
                    emoteType = 3
                )
            }
            ActorAction.POINT_STICK -> {
                val jab = sin(globalTimeSec * 8f) * 10f
                RigPose(
                    offsetY = -idleBounce * 0.4f - talkBounce * 0.5f,
                    bodyTiltDeg = -3f,
                    headTiltDeg = 4f,
                    leftArmDeg = 20f,
                    rightArmDeg = -85f + jab,
                    eyeScaleY = eyeScaleY,
                    mouthOpen = talkMouth,
                    eyebrowOffset = -4f,
                    showPointerStick = true
                )
            }
            ActorAction.SURPRISED_JUMP -> {
                val jumpArc = sin(Easing.clamp01(sceneElapsedSec / 0.75f) * PI.toFloat()) * 135f
                RigPose(
                    offsetY = -jumpArc - talkBounce * 0.4f,
                    scaleX = if (jumpArc > 20f) 0.93f else 1.04f,
                    scaleY = if (jumpArc > 20f) 1.07f else 0.96f,
                    headTiltDeg = -7f,
                    leftArmDeg = 135f,
                    rightArmDeg = -135f,
                    leftLegDeg = 15f,
                    rightLegDeg = -15f,
                    eyeScaleY = 1.22f,
                    mouthOpen = maxOf(talkMouth, 0.75f),
                    eyebrowOffset = -14f,
                    emoteType = 2
                )
            }
            ActorAction.CRY_SHAKE -> {
                val shiver = sin(globalTimeSec * 36f) * 7f
                RigPose(
                    offsetX = shiver,
                    offsetY = 12f + abs(sin(globalTimeSec * 14f)) * 8f,
                    bodyTiltDeg = 4f,
                    headTiltDeg = 8f + sin(globalTimeSec * 18f) * 3f,
                    leftArmDeg = 105f,
                    rightArmDeg = -105f,
                    eyeScaleY = 0.25f,
                    mouthOpen = maxOf(talkMouth, 0.45f),
                    eyebrowOffset = 7f,
                    tearDropLevel = Easing.pingPongWave(globalTimeSec, 1.8f),
                    emoteType = 1
                )
            }
        }
    }

    private fun paletteForRole(role: ActorRole, style: AnimationStyle): RolePalette {
        val base = when (role) {
            ActorRole.RICH_MAN -> RolePalette(
                skin = 0xFFF5CBA7.toInt(),
                skinShadow = 0xFFD9A077.toInt(),
                primary = 0xFF1F2937.toInt(),
                primaryShadow = 0xFF111827.toInt(),
                secondary = 0xFFFBBF24.toInt(),
                accent = 0xFFEF4444.toInt(),
                hair = 0xFF374151.toInt()
            )
            ActorRole.POOR_MAN -> RolePalette(
                skin = 0xFFE5B28A.toInt(),
                skinShadow = 0xFFC58D65.toInt(),
                primary = 0xFF8D6E63.toInt(),
                primaryShadow = 0xFF5D4037.toInt(),
                secondary = 0xFFA1887F.toInt(),
                accent = 0xFF64748B.toInt(),
                hair = 0xFF292524.toInt()
            )
            ActorRole.CHICKEN_MENTOR -> RolePalette(
                skin = 0xFFFFF8E1.toInt(),
                skinShadow = 0xFFFDE68A.toInt(),
                primary = 0xFFFFFFFF.toInt(),
                primaryShadow = 0xFFE2E8F0.toInt(),
                secondary = 0xFFF59E0B.toInt(),
                accent = 0xFFEF4444.toInt(),
                hair = 0xFFEF4444.toInt()
            )
            ActorRole.HERO -> RolePalette(
                skin = 0xFFF3C39B.toInt(),
                skinShadow = 0xFFD49A6E.toInt(),
                primary = 0xFF2563EB.toInt(),
                primaryShadow = 0xFF1D4ED8.toInt(),
                secondary = 0xFFFF5E36.toInt(),
                accent = 0xFFFFD166.toInt(),
                hair = 0xFF1E1B4B.toInt()
            )
            ActorRole.NARRATOR -> RolePalette(
                skin = 0xFFF0C09A.toInt(),
                skinShadow = 0xFFD0976E.toInt(),
                primary = 0xFF0F766E.toInt(),
                primaryShadow = 0xFF115E59.toInt(),
                secondary = 0xFFFDE68A.toInt(),
                accent = 0xFF38BDF8.toInt(),
                hair = 0xFF1F2937.toInt()
            )
        }

        if (style == AnimationStyle.DARK_SUSPENSE) {
            return base.copy(
                skin = darkenColor(base.skin, 0.72f),
                primary = darkenColor(base.primary, 0.68f),
                secondary = darkenColor(base.secondary, 0.85f)
            )
        }
        return base
    }

    private fun drawLayeredPuppet(
        canvas: Canvas,
        rootX: Float,
        rootY: Float,
        u: Float,
        role: ActorRole,
        pose: RigPose,
        style: AnimationStyle,
        silhouetteOutlineOnly: Boolean
    ) {
        val pal = paletteForRole(role, style)
        val isChibi = style == AnimationStyle.CHIBI_STORYTIME
        val isBorderless = (style == AnimationStyle.FLAT_VECTOR || style == AnimationStyle.TECH_EXPLAINER) && !silhouetteOutlineOnly

        val strokeW = when {
            silhouetteOutlineOnly -> 20f * u // yields ~10px thick outer cutout border around whole rig
            isBorderless -> 0f
            style == AnimationStyle.DARK_SUSPENSE -> 5.5f * u
            else -> 4.5f * u
        }

        fun makeFillPaint(c1: Int, c2: Int? = null, yTop: Float = 0f, yBot: Float = 100f): Paint {
            if (silhouetteOutlineOnly) {
                return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    setStyle(Paint.Style.FILL_AND_STROKE)
                    strokeWidth = strokeW
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
            }
            return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (c2 != null && (style == AnimationStyle.FLAT_VECTOR || style == AnimationStyle.TECH_EXPLAINER)) {
                    shader = LinearGradient(rootX, yTop, rootX, yBot, c1, c2, Shader.TileMode.CLAMP)
                } else {
                    color = c1
                }
                setStyle(Paint.Style.FILL)
            }
        }

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (silhouetteOutlineOnly) Color.BLACK else 0xFF0E1018.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = strokeW
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == AnimationStyle.TECH_EXPLAINER) 0x9900E5FF.toInt() else 0xAAFF4D4D.toInt()
            setStyle(Paint.Style.STROKE)
            strokeWidth = 5f * u
            strokeCap = Paint.Cap.ROUND
        }

        val headScale = if (isChibi) 1.32f else 1.0f
        val bodyH = if (isChibi) 145f * u else 185f * u
        val legH = if (isChibi) 88f * u else 130f * u
        val hipY = rootY - legH
        val shoulderY = hipY - bodyH + 25f * u
        val neckY = hipY - bodyH

        // 1. LEGS (Left & Right hierarchical pivots at hip)
        val legSpacing = if (role == ActorRole.CHICKEN_MENTOR) 34f * u else 40f * u
        drawLimb(
            canvas = canvas,
            pivotX = rootX - legSpacing,
            pivotY = hipY,
            length = legH,
            thickness = if (role == ActorRole.CHICKEN_MENTOR) 18f * u else 34f * u,
            angleDeg = pose.leftLegDeg,
            fillPaint = makeFillPaint(
                if (role == ActorRole.CHICKEN_MENTOR) pal.secondary else pal.primaryShadow,
                pal.primary, hipY, rootY
            ),
            outlinePaint = outlinePaint,
            shoeColor = if (role == ActorRole.CHICKEN_MENTOR) pal.secondary else 0xFF111827.toInt(),
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            strokeW = strokeW
        )
        drawLimb(
            canvas = canvas,
            pivotX = rootX + legSpacing,
            pivotY = hipY,
            length = legH,
            thickness = if (role == ActorRole.CHICKEN_MENTOR) 18f * u else 34f * u,
            angleDeg = pose.rightLegDeg,
            fillPaint = makeFillPaint(
                if (role == ActorRole.CHICKEN_MENTOR) pal.secondary else pal.primaryShadow,
                pal.primary, hipY, rootY
            ),
            outlinePaint = outlinePaint,
            shoeColor = if (role == ActorRole.CHICKEN_MENTOR) pal.secondary else 0xFF111827.toInt(),
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            strokeW = strokeW
        )

        // 2. LEFT ARM (behind torso)
        val shoulderOffset = if (role == ActorRole.CHICKEN_MENTOR) 72f * u else 68f * u
        drawArm(
            canvas = canvas,
            pivotX = rootX - shoulderOffset,
            pivotY = shoulderY,
            length = if (isChibi) 115f * u else 140f * u,
            thickness = if (role == ActorRole.CHICKEN_MENTOR) 34f * u else 28f * u,
            angleDeg = pose.leftArmDeg,
            sleevePaint = makeFillPaint(pal.primary, pal.primaryShadow, shoulderY, hipY),
            handPaint = makeFillPaint(if (role == ActorRole.CHICKEN_MENTOR) pal.primary else pal.skin),
            outlinePaint = outlinePaint,
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            strokeW = strokeW,
            isWing = role == ActorRole.CHICKEN_MENTOR,
            isThumbsUp = false,
            u = u
        )

        // 3. TORSO / COSTUME SILHOUETTE
        val torsoRect = when (role) {
            ActorRole.CHICKEN_MENTOR -> RectF(rootX - 88f * u, neckY, rootX + 88f * u, hipY + 18f * u)
            ActorRole.POOR_MAN -> RectF(rootX - 82f * u, neckY + 12f * u, rootX + 82f * u, hipY + 26f * u)
            else -> RectF(rootX - 74f * u, neckY, rootX + 74f * u, hipY + 15f * u)
        }
        val torsoPaint = makeFillPaint(pal.primary, pal.primaryShadow, torsoRect.top, torsoRect.bottom)
        canvas.drawRoundRect(torsoRect, 42f * u, 42f * u, torsoPaint)
        if (strokeW > 0f) canvas.drawRoundRect(torsoRect, 42f * u, 42f * u, outlinePaint)

        if (!silhouetteOutlineOnly) {
            drawRoleTorsoDetails(canvas, torsoRect, rootX, u, role, pal, style)
            if (style == AnimationStyle.DARK_SUSPENSE || style == AnimationStyle.TECH_EXPLAINER) {
                canvas.drawArc(torsoRect, -65f, 110f, false, rimPaint)
            }
        }

        // 4. HEAD GROUP (Hierarchical Matrix pivot at neckY)
        canvas.save()
        val headPivotY = neckY
        val headMatrix = Matrix().apply {
            postRotate(pose.headTiltDeg, rootX, headPivotY)
            postScale(headScale, headScale, rootX, headPivotY)
        }
        canvas.concat(headMatrix)

        val headRadiusX = if (role == ActorRole.CHICKEN_MENTOR) 78f * u else 84f * u
        val headRadiusY = if (role == ActorRole.CHICKEN_MENTOR) 76f * u else 88f * u
        val headCenterY = neckY - headRadiusY + 12f * u
        val headRect = RectF(
            rootX - headRadiusX,
            headCenterY - headRadiusY,
            rootX + headRadiusX,
            headCenterY + headRadiusY
        )

        // Hair / Comb behind or on top of head
        if (role == ActorRole.CHICKEN_MENTOR) {
            drawChickenComb(canvas, rootX, headRect.top, u, pal.accent, outlinePaint, silhouetteOutlineOnly, strokeW)
        }

        val headPaint = makeFillPaint(
            if (role == ActorRole.CHICKEN_MENTOR) pal.primary else pal.skin,
            if (role == ActorRole.CHICKEN_MENTOR) pal.primaryShadow else pal.skinShadow,
            headRect.top, headRect.bottom
        )
        canvas.drawOval(headRect, headPaint)
        if (strokeW > 0f) canvas.drawOval(headRect, outlinePaint)

        if (!silhouetteOutlineOnly) {
            drawFacialFeatures(
                canvas = canvas,
                cx = rootX,
                cy = headCenterY,
                u = u,
                role = role,
                pal = pal,
                pose = pose,
                style = style,
                outlinePaint = outlinePaint
            )
            drawRoleHeadgear(canvas, rootX, headRect, u, role, pal, outlinePaint, style)
            if (style == AnimationStyle.DARK_SUSPENSE || style == AnimationStyle.TECH_EXPLAINER) {
                canvas.drawArc(headRect, -55f, 100f, false, rimPaint)
            }
        } else {
            // Ensure top hat silhouette is included in Paper Diorama 10px border pass!
            if (role == ActorRole.RICH_MAN) {
                drawRoleHeadgear(canvas, rootX, headRect, u, role, pal, outlinePaint, style, silhouetteOnly = true)
            }
        }
        canvas.restore()

        // 5. RIGHT ARM (in front of torso, holds pointer stick or thumbs up)
        drawArm(
            canvas = canvas,
            pivotX = rootX + shoulderOffset,
            pivotY = shoulderY,
            length = if (isChibi) 115f * u else 140f * u,
            thickness = if (role == ActorRole.CHICKEN_MENTOR) 34f * u else 28f * u,
            angleDeg = pose.rightArmDeg,
            sleevePaint = makeFillPaint(pal.primary, pal.primaryShadow, shoulderY, hipY),
            handPaint = makeFillPaint(if (role == ActorRole.CHICKEN_MENTOR) pal.primary else pal.skin),
            outlinePaint = outlinePaint,
            silhouetteOutlineOnly = silhouetteOutlineOnly,
            strokeW = strokeW,
            isWing = role == ActorRole.CHICKEN_MENTOR,
            isThumbsUp = pose.showThumbsUp,
            hasPointerStick = pose.showPointerStick || role == ActorRole.CHICKEN_MENTOR,
            u = u
        )
    }

    private fun drawLimb(
        canvas: Canvas,
        pivotX: Float,
        pivotY: Float,
        length: Float,
        thickness: Float,
        angleDeg: Float,
        fillPaint: Paint,
        outlinePaint: Paint,
        shoeColor: Int,
        silhouetteOutlineOnly: Boolean,
        strokeW: Float
    ) {
        canvas.save()
        canvas.rotate(angleDeg, pivotX, pivotY)
        val rect = RectF(pivotX - thickness * 0.5f, pivotY, pivotX + thickness * 0.5f, pivotY + length)
        canvas.drawRoundRect(rect, thickness * 0.45f, thickness * 0.45f, fillPaint)
        if (strokeW > 0f) canvas.drawRoundRect(rect, thickness * 0.45f, thickness * 0.45f, outlinePaint)

        // Shoe / Claw
        val shoeRect = RectF(
            pivotX - thickness * 0.65f,
            pivotY + length - thickness * 0.4f,
            pivotX + thickness * 0.95f,
            pivotY + length + thickness * 0.35f
        )
        val sp = if (silhouetteOutlineOnly) fillPaint else Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shoeColor }
        canvas.drawRoundRect(shoeRect, thickness * 0.35f, thickness * 0.35f, sp)
        if (strokeW > 0f) canvas.drawRoundRect(shoeRect, thickness * 0.35f, thickness * 0.35f, outlinePaint)
        canvas.restore()
    }

    private fun drawArm(
        canvas: Canvas,
        pivotX: Float,
        pivotY: Float,
        length: Float,
        thickness: Float,
        angleDeg: Float,
        sleevePaint: Paint,
        handPaint: Paint,
        outlinePaint: Paint,
        silhouetteOutlineOnly: Boolean,
        strokeW: Float,
        isWing: Boolean,
        isThumbsUp: Boolean,
        hasPointerStick: Boolean = false,
        u: Float
    ) {
        canvas.save()
        canvas.rotate(angleDeg, pivotX, pivotY)

        if (isWing) {
            val wingRect = RectF(pivotX - thickness * 0.6f, pivotY - 8f * u, pivotX + thickness * 0.6f, pivotY + length * 0.88f)
            canvas.drawOval(wingRect, sleevePaint)
            if (strokeW > 0f) canvas.drawOval(wingRect, outlinePaint)
        } else {
            val armRect = RectF(pivotX - thickness * 0.5f, pivotY, pivotX + thickness * 0.5f, pivotY + length)
            canvas.drawRoundRect(armRect, thickness * 0.45f, thickness * 0.45f, sleevePaint)
            if (strokeW > 0f) canvas.drawRoundRect(armRect, thickness * 0.45f, thickness * 0.45f, outlinePaint)

            val handY = pivotY + length
            canvas.drawCircle(pivotX, handY, thickness * 0.58f, handPaint)
            if (strokeW > 0f) canvas.drawCircle(pivotX, handY, thickness * 0.58f, outlinePaint)

            if (isThumbsUp && !silhouetteOutlineOnly) {
                val thumbRect = RectF(pivotX - 9f * u, handY - 34f * u, pivotX + 9f * u, handY)
                canvas.drawRoundRect(thumbRect, 8f * u, 8f * u, handPaint)
                if (strokeW > 0f) canvas.drawRoundRect(thumbRect, 8f * u, 8f * u, outlinePaint)
            }
        }

        if (hasPointerStick && !silhouetteOutlineOnly) {
            val handY = pivotY + length * (if (isWing) 0.78f else 0.98f)
            val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8B5E3C.toInt()
                strokeWidth = 10f * u
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(pivotX, handY, pivotX + 150f * u, handY - 45f * u, stickPaint)
            val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD166.toInt() }
            canvas.drawCircle(pivotX + 150f * u, handY - 45f * u, 9f * u, tipPaint)
        }
        canvas.restore()
    }

    private fun drawRoleTorsoDetails(
        canvas: Canvas,
        torso: RectF,
        cx: Float,
        u: Float,
        role: ActorRole,
        pal: RolePalette,
        style: AnimationStyle
    ) {
        when (role) {
            ActorRole.RICH_MAN -> {
                // Crisp white V-shirt + gold tie + lapel
                val shirt = Path().apply {
                    moveTo(cx - 28f * u, torso.top)
                    lineTo(cx, torso.top + 88f * u)
                    lineTo(cx + 28f * u, torso.top)
                    close()
                }
                canvas.drawPath(shirt, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                val tie = Path().apply {
                    moveTo(cx - 10f * u, torso.top + 8f * u)
                    lineTo(cx + 10f * u, torso.top + 8f * u)
                    lineTo(cx + 14f * u, torso.top + 96f * u)
                    lineTo(cx, torso.top + 118f * u)
                    lineTo(cx - 14f * u, torso.top + 96f * u)
                    close()
                }
                canvas.drawPath(tie, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary })
                // Gold pocket square
                canvas.drawRect(cx + 26f * u, torso.top + 52f * u, cx + 50f * u, torso.top + 66f * u,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary })
            }
            ActorRole.POOR_MAN -> {
                // Wooden barrel staves & metal hoops around torso!
                val hoopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF475569.toInt()
                    strokeWidth = 10f * u
                }
                canvas.drawLine(torso.left + 4f * u, torso.top + 48f * u, torso.right - 4f * u, torso.top + 48f * u, hoopPaint)
                canvas.drawLine(torso.left + 4f * u, torso.bottom - 42f * u, torso.right - 4f * u, torso.bottom - 42f * u, hoopPaint)
                // Vertical wood plank lines
                val plankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x552B1B12
                    strokeWidth = 4f * u
                }
                for (dx in listOf(-42f, -14f, 14f, 42f)) {
                    canvas.drawLine(cx + dx * u, torso.top + 16f * u, cx + dx * u, torso.bottom - 14f * u, plankPaint)
                }
            }
            ActorRole.HERO -> {
                // Diagonal hero scarf / hoodie accent + chest emblem
                val scarf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary }
                canvas.drawRoundRect(
                    RectF(cx - 56f * u, torso.top - 4f * u, cx + 56f * u, torso.top + 32f * u),
                    16f * u, 16f * u, scarf
                )
                val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent }
                canvas.drawCircle(cx, torso.top + 78f * u, 20f * u, badge)
            }
            ActorRole.NARRATOR -> {
                // Studio blazer collar + lapel mic
                val lapel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary }
                canvas.drawCircle(cx - 26f * u, torso.top + 46f * u, 8f * u, lapel)
                val mic = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt() }
                canvas.drawCircle(cx - 26f * u, torso.top + 46f * u, 5f * u, mic)
            }
            ActorRole.CHICKEN_MENTOR -> {
                // Feather bib + bowtie
                val bib = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primaryShadow }
                canvas.drawArc(
                    RectF(cx - 52f * u, torso.top + 15f * u, cx + 52f * u, torso.top + 95f * u),
                    0f, 180f, true, bib
                )
                val tie = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent }
                canvas.drawCircle(cx, torso.top + 28f * u, 12f * u, tie)
            }
        }
    }

    private fun drawChickenComb(
        canvas: Canvas,
        cx: Float,
        headTop: Float,
        u: Float,
        color: Int,
        outlinePaint: Paint,
        silhouetteOnly: Boolean,
        strokeW: Float
    ) {
        val combPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (silhouetteOnly) Color.BLACK else color
            if (silhouetteOnly) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = strokeW
            }
        }
        for ((dx, r) in listOf(-20f to 20f, 4f to 26f, 28f to 18f)) {
            canvas.drawCircle(cx + dx * u, headTop - 4f * u, r * u, combPaint)
            if (strokeW > 0f && !silhouetteOnly) {
                canvas.drawCircle(cx + dx * u, headTop - 4f * u, r * u, outlinePaint)
            }
        }
    }

    private fun drawFacialFeatures(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        u: Float,
        role: ActorRole,
        pal: RolePalette,
        pose: RigPose,
        style: AnimationStyle,
        outlinePaint: Paint
    ) {
        val isChibi = style == AnimationStyle.CHIBI_STORYTIME
        val eyeXOffset = if (isChibi) 32f * u else 28f * u
        val eyeY = cy - 10f * u
        val eyeR = if (isChibi) 21f * u else 15f * u

        // Blush cheeks for Chibi or joyful expressions
        if (isChibi || pose.showThumbsUp) {
            val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FF6B8B }
            canvas.drawOval(RectF(cx - 54f * u, eyeY + 18f * u, cx - 26f * u, eyeY + 34f * u), blush)
            canvas.drawOval(RectF(cx + 26f * u, eyeY + 18f * u, cx + 54f * u, eyeY + 34f * u), blush)
        }

        // Eyes (with 3.5s blink scaling)
        val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == AnimationStyle.CHIBI_STORYTIME) 0xFF1E1B4B.toInt() else 0xFF0F172A.toInt()
        }
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * eyeXOffset
            canvas.save()
            canvas.scale(1f, pose.eyeScaleY, ex, eyeY)
            canvas.drawCircle(ex, eyeY, eyeR, scleraPaint)
            canvas.drawCircle(ex, eyeY, eyeR, outlinePaint.apply { strokeWidth = 3f * u })
            canvas.drawCircle(ex + side * 2f * u, eyeY, eyeR * 0.58f, pupilPaint)
            canvas.drawCircle(ex - 4f * u, eyeY - 4f * u, eyeR * 0.24f, shinePaint)
            canvas.restore()
        }

        // Expressive Eyebrows
        val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.hair
            strokeWidth = 7f * u
            strokeCap = Paint.Cap.ROUND
        }
        val browY = eyeY - eyeR - 10f * u + pose.eyebrowOffset * u
        canvas.drawLine(cx - eyeXOffset - 16f * u, browY, cx - eyeXOffset + 16f * u, browY - pose.eyebrowOffset * 0.25f * u, browPaint)
        canvas.drawLine(cx + eyeXOffset - 16f * u, browY - pose.eyebrowOffset * 0.25f * u, cx + eyeXOffset + 16f * u, browY, browPaint)

        // Tears when CRY_SHAKE
        if (pose.tearDropLevel > 0f) {
            val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC38BDF8.toInt() }
            val ty = eyeY + 14f * u + pose.tearDropLevel * 32f * u
            canvas.drawCircle(cx - eyeXOffset, ty, 6f * u, tearPaint)
            canvas.drawCircle(cx + eyeXOffset, ty, 6f * u, tearPaint)
        }

        // Rich Man Monocle + Mustache
        if (role == ActorRole.RICH_MAN) {
            val goldRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.secondary
                setStyle(Paint.Style.STROKE)
                strokeWidth = 4.5f * u
            }
            canvas.drawCircle(cx + eyeXOffset, eyeY, eyeR + 5f * u, goldRim)
            canvas.drawLine(cx + eyeXOffset + eyeR + 4f * u, eyeY + 4f * u, cx + eyeXOffset + 22f * u, cy + 44f * u, goldRim)
        }
        // Chicken Mentor round spectacles
        if (role == ActorRole.CHICKEN_MENTOR) {
            val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1F2937.toInt()
                setStyle(Paint.Style.STROKE)
                strokeWidth = 4f * u
            }
            canvas.drawCircle(cx - eyeXOffset, eyeY, eyeR + 5f * u, specPaint)
            canvas.drawCircle(cx + eyeXOffset, eyeY, eyeR + 5f * u, specPaint)
            canvas.drawLine(cx - eyeXOffset + eyeR + 5f * u, eyeY, cx + eyeXOffset - eyeR - 5f * u, eyeY, specPaint)
        }

        // Mouth / Beak (animated dynamically with active word timestamps!)
        val mouthY = cy + 30f * u
        if (role == ActorRole.CHICKEN_MENTOR) {
            val beakColor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary }
            val openGap = pose.mouthOpen * 22f * u
            val upperBeak = Path().apply {
                moveTo(cx - 22f * u, mouthY)
                lineTo(cx + 22f * u, mouthY)
                lineTo(cx, mouthY - 16f * u)
                close()
            }
            val lowerBeak = Path().apply {
                moveTo(cx - 18f * u, mouthY + openGap)
                lineTo(cx + 18f * u, mouthY + openGap)
                lineTo(cx, mouthY + openGap + 15f * u)
                close()
            }
            if (openGap > 3f * u) {
                canvas.drawRect(
                    cx - 16f * u, mouthY - 2f * u, cx + 16f * u, mouthY + openGap + 2f * u,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7F1D1D.toInt() }
                )
            }
            canvas.drawPath(upperBeak, beakColor)
            canvas.drawPath(lowerBeak, beakColor)
        } else {
            val mouthH = (5f + pose.mouthOpen * 26f) * u
            val mouthW = (24f + pose.mouthOpen * 8f) * u
            val mouthRect = RectF(cx - mouthW, mouthY - mouthH * 0.35f, cx + mouthW, mouthY + mouthH * 0.65f)
            val mouthFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7F1D1D.toInt() }
            canvas.drawOval(mouthRect, mouthFill)
            if (pose.mouthOpen > 0.25f) {
                val tongue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFB7185.toInt() }
                canvas.drawArc(mouthRect, 15f, 150f, true, tongue)
            }
            if (role == ActorRole.RICH_MAN) {
                val stache = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.hair }
                canvas.drawOval(RectF(cx - 32f * u, mouthY - 16f * u, cx, mouthY - 2f * u), stache)
                canvas.drawOval(RectF(cx, mouthY - 16f * u, cx + 32f * u, mouthY - 2f * u), stache)
            }
        }
    }

    private fun drawRoleHeadgear(
        canvas: Canvas,
        cx: Float,
        headRect: RectF,
        u: Float,
        role: ActorRole,
        pal: RolePalette,
        outlinePaint: Paint,
        style: AnimationStyle,
        silhouetteOnly: Boolean = false
    ) {
        when (role) {
            ActorRole.RICH_MAN -> {
                val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (silhouetteOnly) Color.BLACK else 0xFF111827.toInt()
                    if (silhouetteOnly) {
                        setStyle(Paint.Style.FILL_AND_STROKE)
                        strokeWidth = 20f * u
                    }
                }
                val brim = RectF(cx - 98f * u, headRect.top - 12f * u, cx + 98f * u, headRect.top + 12f * u)
                val crown = RectF(cx - 60f * u, headRect.top - 98f * u, cx + 60f * u, headRect.top + 2f * u)
                canvas.drawRoundRect(crown, 12f * u, 12f * u, hatPaint)
                canvas.drawRoundRect(brim, 10f * u, 10f * u, hatPaint)
                if (!silhouetteOnly) {
                    val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.secondary }
                    canvas.drawRect(cx - 60f * u, headRect.top - 22f * u, cx + 60f * u, headRect.top - 4f * u, band)
                }
            }
            ActorRole.HERO -> {
                val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.hair }
                val spikes = Path().apply {
                    moveTo(headRect.left + 8f * u, headRect.top + 38f * u)
                    lineTo(cx - 56f * u, headRect.top - 24f * u)
                    lineTo(cx - 24f * u, headRect.top + 2f * u)
                    lineTo(cx + 8f * u, headRect.top - 34f * u)
                    lineTo(cx + 34f * u, headRect.top + 4f * u)
                    lineTo(cx + 68f * u, headRect.top - 18f * u)
                    lineTo(headRect.right - 8f * u, headRect.top + 38f * u)
                    close()
                }
                canvas.drawPath(spikes, hairPaint)
            }
            ActorRole.NARRATOR, ActorRole.POOR_MAN -> {
                val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.hair }
                canvas.drawArc(
                    RectF(headRect.left - 4f * u, headRect.top - 10f * u, headRect.right + 4f * u, headRect.top + 56f * u),
                    180f, 180f, true, hairPaint
                )
            }
            ActorRole.CHICKEN_MENTOR -> Unit
        }
    }

    private fun drawWhiteboardStickman(
        canvas: Canvas,
        rootX: Float,
        rootY: Float,
        u: Float,
        role: ActorRole,
        pose: RigPose,
        globalTimeSec: Float
    ) {
        val wobbleSeed = (globalTimeSec * 4f).toInt().toFloat()
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1D26.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 10f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = DiscretePathEffect(18f * u, (2.8f + (wobbleSeed % 2f) * 0.5f) * u)
        }
        val redMarker = Paint(inkPaint).apply {
            color = 0xFFD9383A.toInt()
            strokeWidth = 8f * u
        }

        canvas.save()
        canvas.rotate(pose.bodyTiltDeg, rootX, rootY - 120f * u)

        val hipY = rootY - 135f * u
        val shoulderY = hipY - 160f * u
        val headCy = shoulderY - 76f * u

        // Legs
        val legPath = Path().apply {
            moveTo(rootX, hipY)
            lineTo(rootX - 44f * u + pose.leftLegDeg * 1.2f * u, rootY)
            moveTo(rootX, hipY)
            lineTo(rootX + 44f * u + pose.rightLegDeg * 1.2f * u, rootY)
        }
        canvas.drawPath(legPath, inkPaint)

        // Spine
        canvas.drawLine(rootX, hipY, rootX, shoulderY, inkPaint)

        // Arms
        val lRad = Math.toRadians((90f + pose.leftArmDeg).toDouble())
        val rRad = Math.toRadians((90f + pose.rightArmDeg).toDouble())
        val armLen = 120f * u
        val lx = rootX - kotlin.math.cos(lRad).toFloat() * armLen
        val ly = shoulderY + kotlin.math.sin(lRad).toFloat() * armLen
        val rx = rootX + kotlin.math.cos(rRad).toFloat() * armLen
        val ry = shoulderY + kotlin.math.sin(rRad).toFloat() * armLen
        canvas.drawLine(rootX, shoulderY + 18f * u, lx, ly, inkPaint)
        canvas.drawLine(rootX, shoulderY + 18f * u, rx, ry, inkPaint)

        if (pose.showPointerStick || role == ActorRole.CHICKEN_MENTOR) {
            canvas.drawLine(rx, ry, rx + 130f * u, ry - 45f * u, redMarker)
        }

        // Head circle
        val headFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAF6EE.toInt() }
        canvas.drawCircle(rootX, headCy, 66f * u, headFill)
        canvas.drawCircle(rootX, headCy, 66f * u, inkPaint)

        // Role cue on stickman (top hat for rich man, comb for chicken)
        if (role == ActorRole.RICH_MAN) {
            canvas.drawRect(rootX - 42f * u, headCy - 110f * u, rootX + 42f * u, headCy - 66f * u, inkPaint)
            canvas.drawLine(rootX - 66f * u, headCy - 66f * u, rootX + 66f * u, headCy - 66f * u, inkPaint)
        } else if (role == ActorRole.CHICKEN_MENTOR) {
            canvas.drawCircle(rootX - 10f * u, headCy - 72f * u, 14f * u, redMarker)
            canvas.drawCircle(rootX + 12f * u, headCy - 76f * u, 16f * u, redMarker)
        }

        // Eyes & talking mouth
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1D26.toInt() }
        val eyeScale = pose.eyeScaleY.coerceAtLeast(0.15f)
        canvas.drawOval(RectF(rootX - 28f * u, headCy - 18f * u * eyeScale, rootX - 14f * u, headCy - 4f * u * eyeScale + 6f * u), dotPaint)
        canvas.drawOval(RectF(rootX + 14f * u, headCy - 18f * u * eyeScale, rootX + 28f * u, headCy - 4f * u * eyeScale + 6f * u), dotPaint)

        val mh = (4f + pose.mouthOpen * 20f) * u
        canvas.drawOval(RectF(rootX - 18f * u, headCy + 20f * u, rootX + 18f * u, headCy + 20f * u + mh), redMarker)

        canvas.restore()
    }

    private fun drawAnimeEmotes(
        canvas: Canvas,
        cx: Float,
        topY: Float,
        u: Float,
        pose: RigPose,
        globalTimeSec: Float,
        style: AnimationStyle
    ) {
        val pulse = 0.85f + 0.15f * sin(globalTimeSec * 8f)
        when (pose.emoteType) {
            1 -> {
                // Anime sweatdrop
                val dropX = cx + 92f * u
                val dropY = topY + 40f * u + (globalTimeSec * 30f % 24f) * u
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF38BDF8.toInt() }
                val path = Path().apply {
                    moveTo(dropX, dropY - 24f * u)
                    quadTo(dropX + 22f * u, dropY + 12f * u, dropX, dropY + 24f * u)
                    quadTo(dropX - 22f * u, dropY + 12f * u, dropX, dropY - 24f * u)
                }
                canvas.drawPath(path, p)
            }
            2 -> {
                // Anime shock / anger vein
                val vx = cx + 96f * u
                val vy = topY + 26f * u
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFEF4444.toInt()
                    setStyle(Paint.Style.STROKE)
                    strokeWidth = 7f * u * pulse
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawArc(RectF(vx - 26f * u, vy - 26f * u, vx, vy), 0f, 90f, false, p)
                canvas.drawArc(RectF(vx + 6f * u, vy - 26f * u, vx + 32f * u, vy), 90f, 90f, false, p)
                canvas.drawArc(RectF(vx - 26f * u, vy + 6f * u, vx, vy + 32f * u), 270f, 90f, false, p)
                canvas.drawArc(RectF(vx + 6f * u, vy + 6f * u, vx + 32f * u, vy + 32f * u), 180f, 90f, false, p)
            }
            3 -> if (style == AnimationStyle.CHIBI_STORYTIME) {
                // Floating anime sparkles
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE066.toInt() }
                for (i in 0 until 3) {
                    val sx = cx + (i - 1) * 105f * u
                    val sy = topY + 18f * u - (i % 2) * 32f * u + sin(globalTimeSec * 5f + i) * 10f * u
                    val s = (16f * pulse) * u
                    val star = Path().apply {
                        moveTo(sx, sy - s)
                        lineTo(sx + s * 0.3f, sy - s * 0.3f)
                        lineTo(sx + s, sy)
                        lineTo(sx + s * 0.3f, sy + s * 0.3f)
                        lineTo(sx, sy + s)
                        lineTo(sx - s * 0.3f, sy + s * 0.3f)
                        lineTo(sx - s, sy)
                        lineTo(sx - s * 0.3f, sy - s * 0.3f)
                        close()
                    }
                    canvas.drawPath(star, sp)
                }
            }
        }
    }

    fun drawMotionGraphicsOverlay(
        canvas: Canvas,
        stageW: Float,
        stageH: Float,
        scene: com.example.model.SceneScript,
        style: AnimationStyle,
        sceneElapsedSec: Float,
        sceneDurationSec: Float,
        globalTimeSec: Float
    ) {
        MotionGraphicsOverlayRenderer.drawOverlays(
            canvas = canvas,
            w = stageW,
            h = stageH,
            scene = scene,
            style = style,
            sceneElapsedSec = sceneElapsedSec,
            sceneDurationSec = sceneDurationSec,
            globalTimeSec = globalTimeSec
        )
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
