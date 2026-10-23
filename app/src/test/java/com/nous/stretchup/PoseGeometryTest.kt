package com.nous.stretchup

import com.nous.stretchup.model.Limb
import com.nous.stretchup.model.Pose
import com.nous.stretchup.model.Stretch
import com.nous.stretchup.ui.jointsFor
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Guards the pose data itself. A hand-authored angle can put a knee on the wrong
 * side of the body, which is exactly how the side lunge ended up as an X, so these
 * invariants are checked against the shipped asset file rather than trusted.
 */
class PoseGeometryTest {

    private fun library(): List<Stretch> {
        val raw = File("src/main/assets/stretches.json").readText()
        val arr = JSONObject(raw).getJSONArray("stretches")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Stretch(
                id = o.getString("id"),
                name = o.getString("name"),
                cue = o.getString("cue"),
                tags = emptyList(),
                twoSided = false,
                props = null,
                poses = (0 until o.getJSONArray("poses").length()).map { j ->
                    val p = o.getJSONArray("poses").getJSONObject(j)
                    val torso = p.getDouble("torso").toFloat()
                    fun limb(k: String) = p.getJSONArray(k).let {
                        Limb(it.getDouble(0).toFloat(), it.getDouble(1).toFloat())
                    }
                    Pose(
                        torso = torso,
                        spine = p.optDouble("spine", torso.toDouble()).toFloat(),
                        head = p.getDouble("head").toFloat(),
                        hipX = p.optDouble("hipX", 0.0).toFloat(),
                        hipY = p.optDouble("hipY", 0.0).toFloat(),
                        pelvisWidth = p.optDouble("pelvis", 9.0).toFloat(),
                        shoulderWidth = p.optDouble("shoulders", 10.0).toFloat(),
                        pelvisAngle = if (p.has("pelvisAngle")) p.getDouble("pelvisAngle").toFloat() else null,
                        chestAngle = if (p.has("chestAngle")) p.getDouble("chestAngle").toFloat() else null,
                        footL = p.optDouble("footL", 180.0).toFloat(),
                        footR = p.optDouble("footR", 0.0).toFloat(),
                        armL = limb("armL"), armR = limb("armR"),
                        legL = limb("legL"), legR = limb("legR")
                    )
                }
            )
        }
    }

    /**
     * A foot may cross the midline on its own - a figure-four or a quad legitimately
     * puts one foot on the far side. Both feet crossing at once means the two legs
     * have been swapped, which is how the side lunge turned into an X.
     */
    @Test
    fun `both feet never cross to the opposite side at once`() {
        library().forEach { s ->
            s.poses.forEach { p ->
                val j = jointsFor(p)
                val both =
                    (j.ankleL.x * j.hipL.x < 0 && abs(j.ankleL.x) < abs(j.hipL.x)) &&
                        (j.ankleR.x * j.hipR.x < 0 && abs(j.ankleR.x) < abs(j.hipR.x))
                assertTrue("${s.id}: both feet crossed (legs swapped?)", !both)
            }
        }
    }

    /**
     * A hand down at floor level while standing is always a data error. (Feet are
     * deliberately not checked for separation: a figure-four or a quad legitimately
     * puts them close together.)
     */
    @Test
    fun `hands stay off the floor`() {
        library().forEach { s ->
            s.poses.forEach { p ->
                val j = jointsFor(p)
                val floor = maxOf(j.ankleL.y, j.ankleR.y)
                val lowestHand = maxOf(j.handL.y, j.handR.y)
                assertTrue(
                    "${s.id}: a hand reaches the floor (hand y=$lowestHand, floor y=$floor)",
                    lowestHand < floor - 1f
                )
            }
        }
    }

    @Test
    fun `every stretch has at least two keyframes and finite angles`() {
        library().forEach { s ->
            assertTrue("${s.id} needs at least two poses", s.poses.size >= 2)
            s.poses.forEach { p ->
                assertTrue("${s.id} has a NaN angle", !p.torso.isNaN() && !p.spine.isNaN() && !p.head.isNaN())
            }
        }
    }
}
