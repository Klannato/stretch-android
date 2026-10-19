package com.nous.stretchup.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** A limb is two segments: [proximal, distal] directions in degrees, screen space. */
data class Limb(val a: Float, val b: Float)

data class Pose(
    val torso: Float,
    val head: Float,
    val hipX: Float,
    val hipY: Float,
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

fun lerpPose(a: Pose, b: Pose, t: Float): Pose = Pose(
    torso = lerpAngle(a.torso, b.torso, t),
    head = lerpAngle(a.head, b.head, t),
    hipX = a.hipX + (b.hipX - a.hipX) * t,
    hipY = a.hipY + (b.hipY - a.hipY) * t,
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

    private fun parsePose(o: JSONObject) = Pose(
        torso = o.getDouble("torso").toFloat(),
        head = o.getDouble("head").toFloat(),
        hipX = o.optDouble("hipX", 0.0).toFloat(),
        hipY = o.optDouble("hipY", 0.0).toFloat(),
        armL = limb(o.getJSONArray("armL")),
        armR = limb(o.getJSONArray("armR")),
        legL = limb(o.getJSONArray("legL")),
        legR = limb(o.getJSONArray("legR"))
    )

    private fun limb(a: JSONArray) = Limb(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())

    fun sanitizePoses(stretch: Stretch): Stretch =
        if (stretch.poses.isEmpty() || stretch.poses.any { it.torso.isNaN() || abs(it.torso) > 720f })
            stretch.copy(poses = listOf(Pose(270f, 270f, 0f, 0f, Limb(90f, 90f), Limb(90f, 90f), Limb(90f, 90f), Limb(90f, 90f))))
        else stretch
}
