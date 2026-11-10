package com.example.core.tether

import com.example.core.camera.Reconnect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameNumberTest {
    @Test
    fun readsCanonFrameNumbers() {
        assertEquals(1234, TetherController.frameNumber("IMG_1234.CR2"))
        assertEquals(42, TetherController.frameNumber("_MG_0042.JPG"))
        assertNull(TetherController.frameNumber("IMG.JPG"))
    }

    @Test
    fun framesAfterTheLastOneSurviveTheCounterStartingOver() {
        assertTrue(TetherController.isAfter(1235, 1234))
        assertFalse(TetherController.isAfter(1234, 1234))
        assertFalse(TetherController.isAfter(1200, 1234))
        // The 50D goes from 9999 back to 0001: 0003 was shot after 9998.
        assertTrue(TetherController.isAfter(3, 9998))
        assertFalse(TetherController.isAfter(9998, 3))
        assertEquals(3, TetherController.latestFrame(listOf(9996, 9997, 9999, 1, 3)))
        assertEquals(1240, TetherController.latestFrame(listOf(1238, 1240, 1239)))
        assertNull(TetherController.latestFrame(emptyList()))
    }

    @Test
    fun reconnectWaitsLongerEachTimeUpToHalfAMinute() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..7).map(Reconnect::delayMs))
    }
}
