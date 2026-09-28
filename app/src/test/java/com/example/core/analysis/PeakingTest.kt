package com.example.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakingTest {
    private val w = 120
    private val h = 80

    @Test
    fun sharpHalfPeaksBlurredHalfDoesNot() {
        // Left half: fine checkerboard (in focus). Right half: the same detail, heavily smoothed.
        val img = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x < w / 2) (if ((x + y) % 2 == 0) 40 else 210) else 125 + ((x / 12 + y / 12) % 2) * 6
        }
        val m = Peaking.mask(img, w, h)
        val left = (1 until h - 1).sumOf { y -> (1 until w / 2 - 1).count { x -> m[y * w + x] } }
        val right = (1 until h - 1).sumOf { y -> (w / 2 + 1 until w - 1).count { x -> m[y * w + x] } }
        assertTrue("left $left right $right", left > 1000 && right == 0)
    }

    @Test
    fun flatFrameHasNoPeaks() {
        assertEquals(0, Peaking.mask(IntArray(w * h) { 128 + (it % 3) }, w, h).count { it })
    }
}
