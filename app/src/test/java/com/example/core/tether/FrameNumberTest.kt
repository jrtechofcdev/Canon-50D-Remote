package com.example.core.tether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameNumberTest {
    @Test
    fun readsCanonFrameNumbers() {
        assertEquals(1234, TetherController.frameNumber("IMG_1234.CR2"))
        assertEquals(42, TetherController.frameNumber("_MG_0042.JPG"))
        assertNull(TetherController.frameNumber("IMG.JPG"))
    }
}
