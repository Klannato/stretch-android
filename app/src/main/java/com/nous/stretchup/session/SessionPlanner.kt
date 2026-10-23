package com.nous.stretchup.session

import com.nous.stretchup.model.Stretch

enum class Slot(val tag: String, val label: String) {
    WAKE("am", "Morning"),
    BED("pm", "Evening")
}

/**
 * A five-minute session is five 60-second blocks. Two-sided stretches split their
 * block in half so both sides get 30 seconds.
 */
data class Block(val stretch: Stretch, val secondSide: Boolean = false) {
    val twoSided get() = stretch.twoSided
}

object SessionPlanner {

    const val BLOCKS = 5
    const val BLOCK_SECONDS = 60
    const val TOTAL_SECONDS = BLOCKS * BLOCK_SECONDS // 300 = five minutes

    fun pool(library: List<Stretch>, slot: Slot): List<Stretch> {
        val tagged = library.filter { slot.tag in it.tags }
        return if (tagged.size >= BLOCKS) tagged else library
    }

    /** Returns the five blocks plus the cursor for the next session. */
    fun plan(library: List<Stretch>, cursor: Int, slot: Slot): Pair<List<Block>, Int> {
        val pool = pool(library, slot)
        if (pool.isEmpty()) return emptyList<Block>() to cursor
        val start = ((cursor % pool.size) + pool.size) % pool.size
        val stretches = (0 until BLOCKS.coerceAtMost(pool.size)).map { pool[(start + it) % pool.size] }
        val next = (start + stretches.size) % pool.size
        return stretches.map { Block(it) } to next
    }

    /** Which of the five blocks the clock is currently in. */
    fun blockIndexFor(elapsed: Float): Int =
        (elapsed / BLOCK_SECONDS).toInt().coerceIn(0, BLOCKS - 1)

    fun secondsIntoBlock(elapsed: Float): Float = elapsed - blockIndexFor(elapsed) * BLOCK_SECONDS

    /** True during the 2-second "switch sides" beat in the middle of a two-sided block. */
    fun switchingSide(block: Block, secondsInto: Float): Boolean =
        block.twoSided && secondsInto in 28f..32f

    /**
     * True for the back half of a two-sided block. The UI renders the same pose
     * mirrored, which is how one drawing serves both sides of a stretch.
     */
    fun mirrorActive(block: Block, secondsInto: Float): Boolean =
        block.twoSided && secondsInto >= 30f
}
