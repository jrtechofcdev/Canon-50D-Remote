package com.example.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposurePresetTest {

    @Test
    fun roundTripsIncludingEmptyFields() {
        val list = listOf(
            ExposurePreset("Igreja interna", iso = 0x68, aperture = 0x15, shutter = 0x68, exposureCompensation = 0xFD, whiteBalance = 3),
            ExposurePreset("Externa | sol", iso = 0x48),
        )
        val decoded = ExposurePreset.decode(ExposurePreset.encode(list))
        assertEquals(list[0], decoded[0])
        assertEquals("Externa / sol", decoded[1].name)
        assertEquals(0x48, decoded[1].iso)
        assertEquals(null, decoded[1].shutter)
    }

    @Test
    fun ignoresBrokenLines() {
        assertTrue(ExposurePreset.decode("lixo\n|1|2|3|4|5\n").isEmpty())
    }
}
