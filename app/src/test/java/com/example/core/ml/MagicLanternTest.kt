package com.example.core.ml

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MagicLanternTest {

    /** GetMenus as ML's ptp-ml.c builds it: 4 unused bytes ('-'), a 32-bit length, the name. */
    private fun menusData(vararg names: String): ByteArray {
        val size = names.sumOf { 8 + it.length }
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        names.forEach { name ->
            b.put(ByteArray(4) { '-'.code.toByte() })
            b.putInt(name.length)
            b.put(name.toByteArray(Charsets.ISO_8859_1))
        }
        return b.array()
    }

    @Test
    fun menuNamesAreReadInOrder() {
        assertEquals(listOf("Audio", "Expo", "Overlay", "Movie", "Shoot"), MlPtp.parseMenus(menusData("Audio", "Expo", "Overlay", "Movie", "Shoot")))
    }

    @Test
    fun aTruncatedAnswerKeepsWhatIsWhole() {
        val data = menusData("Movie", "Debug")
        assertEquals(listOf("Movie"), MlPtp.parseMenus(data.copyOf(data.size - 2)))
        assertEquals(emptyList<String>(), MlPtp.parseMenus(ByteArray(0)))
    }

    @Test
    fun firmwareKeysRoundTrip() {
        CameraFirmware.entries.forEach { assertEquals(it, CameraFirmware.of(it.key)) }
        assertEquals(CameraFirmware.AUTO, CameraFirmware.of("algo"))
    }
}
