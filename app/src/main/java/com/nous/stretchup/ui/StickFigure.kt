package com.nous.stretchup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nous.stretchup.model.Limb
import com.nous.stretchup.model.Pose
import com.nous.stretchup.model.Stretch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TORSO_LOW = 26f   // hip -> lumbar
private const val TORSO_UP = 22f    // lumbar -> neck
private const val UPPER_ARM = 24f
private const val FOREARM = 22f
private const val THIGH = 26f
private const val SHIN = 26f
private const val NECK_HEAD = 13f
private const val HEAD_R = 8.5f
private const val FOOT = 9f

private fun unit(deg: Float): Offset {
    val r = deg * PI / 180.0
    return Offset(cos(r).toFloat(), sin(r).toFloat())
}

private fun Offset.along(dirDeg: Float, len: Float) = this + unit(dirDeg) * len

/** All joints in screen-space local units, hip at origin, y growing downwards. */
data class Joints(
    val hip: Offset,
    val lumbar: Offset,
    val neck: Offset,
    val headCenter: Offset,
    val hipL: Offset,
    val hipR: Offset,
    val shoulderL: Offset,
    val shoulderR: Offset,
    val elbowL: Offset,
    val handL: Offset,
    val elbowR: Offset,
    val handR: Offset,
    val kneeL: Offset,
    val ankleL: Offset,
    val kneeR: Offset,
    val ankleR: Offset,
    val toeL: Offset,
    val toeR: Offset
) {
    val all: List<Offset>
        get() = listOf(
            hip, lumbar, neck, headCenter, hipL, hipR, shoulderL, shoulderR,
            elbowL, handL, elbowR, handR, kneeL, ankleL, kneeR, ankleR, toeL, toeR
        )
}

fun jointsFor(pose: Pose): Joints {
    val hip = Offset(pose.hipX, -pose.hipY)

    // The spine has a bend point, so a side bend or a fold actually bends.
    val lumbar = hip.along(pose.torso, TORSO_LOW)
    val neck = lumbar.along(pose.spine, TORSO_UP)
    val headCenter = neck.along(pose.head, NECK_HEAD + HEAD_R)

    val pelvisDir = unit(pose.pelvisAngle ?: pose.torso)
    val chestDir = unit(pose.chestAngle ?: pose.spine)
    val hipL = hip + pelvisDir.perp() * (pose.pelvisWidth / 2f)
    val hipR = hip - pelvisDir.perp() * (pose.pelvisWidth / 2f)
    val shoulderR = neck + chestDir.perp() * (pose.shoulderWidth / 2f)
    val shoulderL = neck - chestDir.perp() * (pose.shoulderWidth / 2f)

    fun arm(shoulder: Offset, limb: Limb): Pair<Offset, Offset> {
        val elbow = shoulder.along(limb.a, UPPER_ARM)
        return elbow to elbow.along(limb.b, FOREARM)
    }

    fun leg(origin: Offset, limb: Limb): Pair<Offset, Offset> {
        val knee = origin.along(limb.a, THIGH)
        return knee to knee.along(limb.b, SHIN)
    }

    val (elbowL, handL) = arm(shoulderL, pose.armL)
    val (elbowR, handR) = arm(shoulderR, pose.armR)
    val (kneeL, ankleL) = leg(hipL, pose.legL)
    val (kneeR, ankleR) = leg(hipR, pose.legR)

    val toeL = ankleL.along(pose.footL, FOOT)
    val toeR = ankleR.along(pose.footR, FOOT)

    return Joints(
        hip, lumbar, neck, headCenter, hipL, hipR, shoulderL, shoulderR,
        elbowL, handL, elbowR, handR, kneeL, ankleL, kneeR, ankleR, toeL, toeR
    )
}

/** Rotates a direction 90 degrees, giving the axis the bar lies along. */
private fun Offset.perp(): Offset = Offset(-y, x)

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

        // Line weight follows the canvas, not the figure's fit scale: scaling it with
        // `scale` made every limb a blob as soon as the box got large.
        val stroke = (size.height * 0.022f).coerceIn(2.5f, 30f)
        val handRadius = stroke * 0.55f

        if (showFloor) {
            drawLine(
                color = ink.copy(alpha = 0.12f),
                start = Offset(size.width * 0.06f, footY),
                end = Offset(size.width * 0.94f, footY),
                strokeWidth = (2f * scale).coerceAtLeast(1f),
                cap = StrokeCap.Round
            )
        }

        fun seg(a: Offset, b: Offset, w: Float = stroke) =
            drawLine(ink, p(a), p(b), strokeWidth = w, cap = StrokeCap.Round)

        // spine, bent at the lumbar point, plus the pelvis and chest bars
        seg(j.hip, j.lumbar)
        seg(j.lumbar, j.neck)
        drawLine(ink, p(j.hipL), p(j.hipR), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
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

        // legs, hanging from the two hip joints
        seg(j.hipL, j.kneeL)
        seg(j.kneeL, j.ankleL)
        seg(j.hipR, j.kneeR)
        seg(j.kneeR, j.ankleR)

        // feet, pointing where the pose says
        seg(j.ankleL, j.toeL)
        seg(j.ankleR, j.toeR)

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
            style = Stroke(width = stroke * 0.95f)
        )
    }
}
