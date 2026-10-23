package com.nous.stretchup

import com.nous.stretchup.data.VideoLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Filename -> stretch mapping, which is what makes bulk import work without a wizard. */
class VideoLibraryTest {

    private val ids = listOf(
        "side-bend", "neck-side", "shoulder-cross", "chest-open", "triceps",
        "side-lunge", "hip-flexor", "quad", "calf", "fig-four", "twist", "sunrise"
    )

    @Test
    fun `exact name matches`() {
        assertEquals("side-bend", VideoLibrary.stretchIdForFileName("side-bend.mp4", ids))
    }

    @Test
    fun `angle suffixes still match`() {
        assertEquals("side-bend", VideoLibrary.stretchIdForFileName("side-bend_2.mp4", ids))
        assertEquals("side-bend", VideoLibrary.stretchIdForFileName("side-bend-angle1.mov", ids))
        assertEquals("side-bend", VideoLibrary.stretchIdForFileName("side bend front.MP4", ids))
    }

    @Test
    fun `the longer id wins so side-lunge never resolves to something else`() {
        assertEquals("side-lunge", VideoLibrary.stretchIdForFileName("side-lunge_3.mp4", ids))
    }

    @Test
    fun `unrelated names do not match`() {
        assertNull(VideoLibrary.stretchIdForFileName("VID_20261023_184512.mp4", ids))
        assertNull(VideoLibrary.stretchIdForFileName("stretch.mp4", ids))
    }

    @Test
    fun `a hyphenated prefix of a real id does not match`() {
        // "side" is a prefix of "side-bend" but is not itself a stretch
        assertNull(VideoLibrary.stretchIdForFileName("side_2.mp4", ids))
    }
}
