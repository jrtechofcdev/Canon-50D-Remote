package com.example.core.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageAnalyzerTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun flagsPixelsWhereAnyChannelClips() {
        val pixels = intArrayOf(argb(255, 10, 10), argb(120, 120, 120), argb(10, 10, 252), argb(0, 0, 0))
        val m = ImageAnalyzer.measure(pixels, threshold = 250)
        assertArrayEquals(booleanArrayOf(true, false, true, false), m.clipped)
        assertEquals(2, m.clippedCount)
    }

    @Test
    fun binsEachChannelSeparately() {
        val m = ImageAnalyzer.measure(intArrayOf(argb(255, 0, 128)), threshold = 255)
        assertEquals(1, m.red[63])
        assertEquals(1, m.green[0])
        assertEquals(1, m.blue[32])
        assertEquals(1, m.luma.sum())
    }
}
