package com.nous.stretchup.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** A limb is two segments: [proximal, distal] directions in degrees, screen space. */
data class Limb(val a: Float, val b: Float)

/**
 * A pose is a set of limb *directions*, not pixel coordinates.
 *
 * The skeleton has a bend point in the spine (lumbar) and two hip joints, which is
 * what makes side bends, forward folds and twists readable; a single straight torso
 * can only fake them.
 */
data class Pose(
    val torso: Float,                 // hip -> lumbar
    val spine: Float,                 // lumbar -> neck
    val head: Float,
    val hipX: Float,
    val hipY: Float,
    val pelvisWidth: Float = 9f,      // distance between the two hip joints
    val shoulderWidth: Float = 10f,   // distance between the two shoulder joints
    val pelvisAngle: Float? = null,   // defaults to `torso`
    val chestAngle: Float? = null,    // defaults to `spine`
    val footL: Float = 180f,          // foot direction, degrees
    val footR: Float = 0f,
    val armL: Limb,
    val armR: Limb,
    val legL: Limb,
    val legR: Limb
)

data class Stretch(
    val id: String,
    val name: String,
    val cue: String,
    val tags: List<String>,
    val twoSided: Boolean,
    val props: String?,
    val poses: List<Pose>
)

/** Shortest-path angle interpolation, so 350 -> 10 goes the short way round. */
fun lerpAngle(a: Float, b: Float, t: Float): Float {
    var delta = (b - a) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return a + delta * t
}

private fun lerpLimb(a: Limb, b: Limb, t: Float) = Limb(
    lerpAngle(a.a, b.a, t),
    lerpAngle(a.b, b.b, t)
)

private fun lerpNullable(a: Float?, b: Float?, t: Float): Float? =
    if (a == null || b == null) null else lerpAngle(a, b, t)

fun lerpPose(a: Pose, b: Pose, t: Float): Pose = Pose(
    torso = lerpAngle(a.torso, b.torso, t),
    spine = lerpAngle(a.spine, b.spine, t),
    head = lerpAngle(a.head, b.head, t),
    hipX = a.hipX + (b.hipX - a.hipX) * t,
    hipY = a.hipY + (b.hipY - a.hipY) * t,
    pelvisWidth = a.pelvisWidth + (b.pelvisWidth - a.pelvisWidth) * t,
    shoulderWidth = a.shoulderWidth + (b.shoulderWidth - a.shoulderWidth) * t,
    pelvisAngle = lerpNullable(a.pelvisAngle, b.pelvisAngle, t),
    chestAngle = lerpNullable(a.chestAngle, b.chestAngle, t),
    footL = lerpAngle(a.footL, b.footL, t),
    footR = lerpAngle(a.footR, b.footR, t),
    armL = lerpLimb(a.armL, b.armL, t),
    armR = lerpLimb(a.armR, b.armR, t),
    legL = lerpLimb(a.legL, b.legL, t),
    legR = lerpLimb(a.legR, b.legR, t)
)

/** Pose at a point in the breathing cycle (0..1, ping-pongs between the keyframes). */
fun Stretch.poseAt(cycle: Float): Pose {
    if (poses.size < 2) return poses.first()
    val t = (1f - kotlin.math.cos(cycle * 2.0 * Math.PI).toFloat()) / 2f
    return lerpPose(poses[0], poses[1], t.coerceIn(0f, 1f))
}

object StretchLibrary {

    private val STANDING = Pose(
        torso = 270f, spine = 270f, head = 270f, hipX = 0f, hipY = 0f,
        pelvisWidth = 9f, shoulderWidth = 10f, pelvisAngle = null, chestAngle = null,
        footL = 180f, footR = 0f,
        armL = Limb(90f, 90f), armR = Limb(90f, 90f),
        legL = Limb(90f, 90f), legR = Limb(90f, 90f)
    )

    fun load(context: Context): List<Stretch> {
        val raw = context.assets.open("stretches.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val arr: JSONArray = root.getJSONArray("stretches")
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { parse(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun parse(o: JSONObject): Stretch {
        val poses = o.getJSONArray("poses").let { ps ->
            (0 until ps.length()).map { parsePose(ps.getJSONObject(it)) }
        }
        val tags = o.optJSONArray("tags")?.let { t ->
            (0 until t.length()).map { t.getString(it) }
        } ?: emptyList()
        return Stretch(
            id = o.getString("id"),
            name = o.getString("name"),
            cue = o.getString("cue"),
            tags = tags,
            twoSided = o.optString("sides", "one") == "two",
            props = o.optString("props", "").ifBlank { null },
            poses = poses
        )
    }

    private fun parsePose(o: JSONObject): Pose {
        val torso = o.getDouble("torso").toFloat()
        return Pose(
            torso = torso,
            spine = o.optDouble("spine", torso.toDouble()).toFloat(),
            head = o.getDouble("head").toFloat(),
            hipX = o.optDouble("hipX", 0.0).toFloat(),
            hipY = o.optDouble("hipY", 0.0).toFloat(),
            pelvisWidth = o.optDouble("pelvis", 9.0).toFloat(),
            shoulderWidth = o.optDouble("shoulders", 10.0).toFloat(),
            pelvisAngle = if (o.has("pelvisAngle")) o.getDouble("pelvisAngle").toFloat() else null,
            chestAngle = if (o.has("chestAngle")) o.getDouble("chestAngle").toFloat() else null,
            footL = o.optDouble("footL", 180.0).toFloat(),
            footR = o.optDouble("footR", 0.0).toFloat(),
            armL = limb(o.getJSONArray("armL")),
            armR = limb(o.getJSONArray("armR")),
            legL = limb(o.getJSONArray("legL")),
            legR = limb(o.getJSONArray("legR"))
        )
    }

    private fun limb(a: JSONArray) = Limb(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())

    /** Guard against a malformed stretch entry so one bad pose can't blank the screen. */
    fun sanitizePoses(stretch: Stretch): Stretch {
        val bad = stretch.poses.isEmpty() ||
            stretch.poses.any { it.torso.isNaN() || abs(it.torso) > 720f || it.spine.isNaN() }
        return if (bad) stretch.copy(poses = listOf(STANDING)) else stretch
    }
}
