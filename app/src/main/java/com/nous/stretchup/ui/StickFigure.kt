package com.nous.stretchup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nous.stretchup.model.Pose
import com.nous.stretchup.model.Stretch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TORSO = 46f
private const val UPPER_ARM = 24f
private const val FOREARM = 22f
private const val THIGH = 26f
private const val SHIN = 26f
private const val NECK_HEAD = 14f
private const val HEAD_R = 8.5f
private const val SHOULDER_SPREAD = 9f
private const val FOOT = 9f

private fun unit(deg: Float): Offset {
    val r = deg * PI / 180.0
    return Offset(cos(r).toFloat(), sin(r).toFloat())
}

/** All joints in screen-space local units, hip at origin, y growing downwards. */
data class Joints(
    val hip: Offset,
    val neck: Offset,
    val headCenter: Offset,
    val shoulderL: Offset,
    val shoulderR: Offset,
    val elbowL: Offset,
    val handL: Offset,
    val elbowR: Offset,
    val handR: Offset,
    val kneeL: Offset,
    val ankleL: Offset,
    val kneeR: Offset,
    val ankleR: Offset
) {
    val all: List<Offset>
        get() = listOf(
            hip, neck, headCenter, shoulderL, shoulderR,
            elbowL, handL, elbowR, handR, kneeL, ankleL, kneeR, ankleR
        )
}

fun jointsFor(pose: Pose): Joints {
    val hip = Offset(pose.hipX, -pose.hipY)
    val neck = hip + unit(pose.torso) * TORSO
    val headCenter = neck + unit(pose.head) * (NECK_HEAD + HEAD_R)
    val perp = unit(pose.torso + 90f)
    val shoulderR = neck + perp * SHOULDER_SPREAD
    val shoulderL = neck - perp * SHOULDER_SPREAD

    fun arm(shoulder: Offset, limb: com.nous.stretchup.model.Limb): Pair<Offset, Offset> {
        val elbow = shoulder + unit(limb.a) * UPPER_ARM
        return elbow to (elbow + unit(limb.b) * FOREARM)
    }

    fun leg(limb: com.nous.stretchup.model.Limb): Pair<Offset, Offset> {
        val knee = hip + unit(limb.a) * THIGH
        return knee to (knee + unit(limb.b) * SHIN)
    }

    val (elbowL, handL) = arm(shoulderL, pose.armL)
    val (elbowR, handR) = arm(shoulderR, pose.armR)
    val (kneeL, ankleL) = leg(pose.legL)
    val (kneeR, ankleR) = leg(pose.legR)

    return Joints(
        hip, neck, headCenter, shoulderL, shoulderR,
        elbowL, handL, elbowR, handR,
        kneeL, ankleL, kneeR, ankleR
    )
}

/**
 * Bounding box across both keyframes and both sides, so scale and centre stay
 * fixed for a whole 60-second block instead of jumping on every breath.
 */
data class FigureBounds(val minX: Float, val maxX: Float, val maxY: Float, val minY: Float) {
    val width get() = (maxX - minX).coerceAtLeast(1f)
    val height get() = (maxY - minY).coerceAtLeast(1f)
    val centerX get() = (minX + maxX) / 2f
}

fun figureBounds(stretch: Stretch): FigureBounds {
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    stretch.poses.forEach { pose ->
        val joints = jointsFor(pose)
        for (mirrored in booleanArrayOf(false, true)) {
            joints.all.forEach { o ->
                val x = if (mirrored) -o.x else o.x
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (o.y < minY) minY = o.y
                if (o.y > maxY) maxY = o.y
            }
        }
    }
    if (minX > maxX) return FigureBounds(-30f, 30f, 52f, -68f)
    // A wall stretch needs the wall itself inside the frame, not clipped at the edge.
    val rightPad = if (stretch.props == "wall") 26f else 8f
    return FigureBounds(minX - 8f, maxX + rightPad, maxY + 4f, minY - 10f)
}

/**
 * Vector stick figure. Geometry lives in local units (~120 tall) and is fitted to
 * whatever box it is given, so it stays crisp at any size.
 */
@Composable
fun StickFigure(
    pose: Pose,
    bounds: FigureBounds,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    ink: Color = Color(0xFF1F2A26),
    accent: Color = Color(0xFF1F6F5C),
    showFloor: Boolean = true,
    showWall: Boolean = false
) {
    val j = jointsFor(pose)

    Canvas(modifier = modifier) {
        val inset = 0.94f
        val scale = min(
            size.width * inset / bounds.width,
            size.height * inset / bounds.height
        )
        val footY = size.height * 0.93f
        fun p(o: Offset): Offset {
            val x = if (mirrored) -o.x else o.x
            return Offset(
                size.width / 2f + (x - bounds.centerX) * scale,
                footY + (o.y - bounds.maxY) * scale
            )
        }

        val stroke = (5.2f * scale).coerceAtLeast(2f)
        val handRadius = (3.4f * scale).coerceAtLeast(1.5f)

        if (showFloor) {
            drawLine(
                color = ink.copy(alpha = 0.12f),
                start = Offset(size.width * 0.06f, footY),
                end = Offset(size.width * 0.94f, footY),
                strokeWidth = (2f * scale).coerceAtLeast(1f),
                cap = StrokeCap.Round
            )
        }

        fun seg(a: Offset, b: Offset) =
            drawLine(ink, p(a), p(b), strokeWidth = stroke, cap = StrokeCap.Round)

        // spine + shoulder bar
        seg(j.hip, j.neck)
        drawLine(ink, p(j.shoulderL), p(j.shoulderR), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
        // neck
        seg(j.neck, j.neck + (j.headCenter - j.neck) * 0.55f)

        // arms
        seg(j.shoulderL, j.elbowL)
        seg(j.elbowL, j.handL)
        seg(j.shoulderR, j.elbowR)
        seg(j.elbowR, j.handR)
        drawCircle(accent, radius = handRadius, center = p(j.handL))
        drawCircle(accent, radius = handRadius, center = p(j.handR))

        // legs
        seg(j.hip, j.kneeL)
        seg(j.kneeL, j.ankleL)
        seg(j.hip, j.kneeR)
        seg(j.kneeR, j.ankleR)

        // feet
        val footL = Offset(j.ankleL.x - FOOT * 0.55f, j.ankleL.y + 3f)
        val footR = Offset(j.ankleR.x + FOOT * 0.55f, j.ankleR.y + 3f)
        seg(j.ankleL, footL)
        seg(j.ankleR, footR)

        if (showWall) {
            val wallX = bounds.maxX - 8f
            drawLine(
                color = ink.copy(alpha = 0.22f),
                start = p(Offset(wallX, bounds.minY)),
                end = p(Offset(wallX, bounds.maxY)),
                strokeWidth = (3f * scale).coerceAtLeast(1.5f),
                cap = StrokeCap.Round
            )
        }

        // head
        drawCircle(
            color = ink,
            radius = HEAD_R * scale,
            center = p(j.headCenter),
            style = Stroke(width = stroke)
        )
    }
}
