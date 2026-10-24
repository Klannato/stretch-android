package com.nous.stretchup

import com.nous.stretchup.model.Stretch
import com.nous.stretchup.session.SessionPlanner
import com.nous.stretchup.session.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlannerTest {

    private fun stretch(id: String, tags: List<String>, twoSided: Boolean = false) = Stretch(
        id = id,
        name = id,
        cue = "",
        tags = tags,
        twoSided = twoSided
    )

    private val library = listOf(
        stretch("a1", listOf("am"), true),
        stretch("a2", listOf("am")),
        stretch("a3", listOf("am"), true),
        stretch("a4", listOf("am")),
        stretch("a5", listOf("am")),
        stretch("a6", listOf("am"), true),
        stretch("p1", listOf("pm")),
        stretch("p2", listOf("pm"), true),
        stretch("p3", listOf("pm")),
        stretch("p4", listOf("pm")),
        stretch("p5", listOf("pm"), true),
        stretch("p6", listOf("pm")),
        stretch("both", listOf("am", "pm"))
    )

    @Test
    fun `a session is exactly five minutes of five one-minute blocks`() {
        assertEquals(300, SessionPlanner.TOTAL_SECONDS)
        assertEquals(5, SessionPlanner.BLOCKS)
        assertEquals(60, SessionPlanner.BLOCK_SECONDS)
        val (blocks, _) = SessionPlanner.plan(library, 0, Slot.WAKE)
        assertEquals(5, blocks.size)
    }

    @Test
    fun `no stretch repeats inside one session`() {
        for (cursor in 0 until 20) {
            val (blocks, _) = SessionPlanner.plan(library, cursor, Slot.WAKE)
            val ids = blocks.map { it.stretch.id }
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `sessions rotate so two in a row are different`() {
        val (first, next) = SessionPlanner.plan(library, 0, Slot.WAKE)
        val (second, _) = SessionPlanner.plan(library, next, Slot.WAKE)
        assertNotEquals(first.map { it.stretch.id }, second.map { it.stretch.id })
    }

    @Test
    fun `rotation wraps around and stays inside the pool`() {
        var cursor = 0
        val seen = mutableSetOf<String>()
        repeat(12) {
            val (blocks, next) = SessionPlanner.plan(library, cursor, Slot.WAKE)
            blocks.forEach { b ->
                assertTrue("unexpected stretch ${b.stretch.id}", b.stretch.tags.contains("am"))
                seen += b.stretch.id
            }
            cursor = next
        }
        assertEquals(setOf("a1", "a2", "a3", "a4", "a5", "a6", "both"), seen)
    }

    @Test
    fun `evening sessions only draw from the evening pool`() {
        repeat(6) { i ->
            val (blocks, _) = SessionPlanner.plan(library, i, Slot.BED)
            blocks.forEach { b -> assertTrue(b.stretch.tags.contains("pm")) }
        }
    }

    @Test
    fun `any cursor value is safe including negative`() {
        val (blocks, next) = SessionPlanner.plan(library, -7, Slot.WAKE)
        assertEquals(5, blocks.size)
        assertTrue("cursor $next out of range", next in 0 until SessionPlanner.pool(library, Slot.WAKE).size)
    }

    @Test
    fun `block timing maps to five blocks of sixty seconds`() {
        assertEquals(0, SessionPlanner.blockIndexFor(0f))
        assertEquals(0, SessionPlanner.blockIndexFor(59.9f))
        assertEquals(1, SessionPlanner.blockIndexFor(60f))
        assertEquals(4, SessionPlanner.blockIndexFor(299.5f))
        assertEquals(4, SessionPlanner.blockIndexFor(300f))
        assertEquals(0f, SessionPlanner.secondsIntoBlock(120f), 0.001f)
        assertEquals(30f, SessionPlanner.secondsIntoBlock(90f), 0.001f)
    }

    @Test
    fun `two sided stretches switch at the halfway mark`() {
        val (blocks, _) = SessionPlanner.plan(library, 0, Slot.WAKE)
        val twoSided = blocks.first { it.twoSided }
        assertTrue(SessionPlanner.switchingSide(twoSided, 29f))
        assertTrue(!SessionPlanner.switchingSide(twoSided, 45f))
        assertTrue(!SessionPlanner.mirrorActive(twoSided, 29f))
        assertTrue(SessionPlanner.mirrorActive(twoSided, 31f))
    }
}
