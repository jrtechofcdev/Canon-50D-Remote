package com.example.core.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class BracketTest {

    @Test
    fun offsetsFollowCanonOrder() {
        assertEquals(listOf(0, -3, 3), Bracket.offsets(3, 3))
        assertEquals(listOf(0, -2, 2, -4, 4), Bracket.offsets(5, 2))
        assertEquals(7, Bracket.offsets(7, 1).size)
        assertEquals(3, Bracket.offsets(2, 3).size) // invalid counts snap to a valid odd count
    }

    @Test
    fun compensationStaysOnValidThirdsAndWithinTwoStops() {
        assertEquals(0x08, Bracket.compensation(0, 3))          // 0 + 1 EV
        assertEquals(0xF8, Bracket.compensation(0, -3))         // 0 − 1 EV
        assertEquals(0x05, Bracket.compensation(0x03, 1))       // +1/3 + 1/3 = +2/3 (code 5, not 6)
        assertEquals(0x10, Bracket.compensation(0x0D, 6))       // clamped at +2
        assertEquals(0xF0, Bracket.compensation(0xF8, -9))      // clamped at −2
    }

    @Test
    fun shutterMovesTheRightWayInManual() {
        // 1/125 is 0x70. One stop brighter = 1/60 (0x68), one stop darker = 1/250 (0x78).
        assertEquals(0x68, Bracket.shutter(0x70, 3))
        assertEquals(0x78, Bracket.shutter(0x70, -3))
        // 1/100 (0x6D) + 1/3 stop = 1/80 (0x6B)
        assertEquals(0x6B, Bracket.shutter(0x6D, 1))
    }
}
