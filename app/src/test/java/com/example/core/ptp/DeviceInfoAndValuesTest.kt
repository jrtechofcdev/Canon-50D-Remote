package com.example.core.ptp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DeviceInfoAndValuesTest {

    private fun ByteBuffer.ptpString(s: String) {
        if (s.isEmpty()) { put(0); return }
        put((s.length + 1).toByte())
        s.forEach { putShort(it.code.toShort()) }
        putShort(0)
    }

    private fun ByteBuffer.u16Array(vararg v: Int) {
        putInt(v.size)
        v.forEach { putShort(it.toShort()) }
    }

    @Test
    fun parsesModelAndSupportedOperations() {
        val b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(100); b.putInt(11); b.putShort(100); b.ptpString(""); b.putShort(0)
        b.u16Array(PtpOp.OPEN_SESSION, PtpOp.GET_OBJECT, PtpOp.EOS_REMOTE_RELEASE, PtpOp.EOS_GET_EVENT)
        b.u16Array(); b.u16Array(); b.u16Array(); b.u16Array()
        b.ptpString("Canon Inc."); b.ptpString("Canon EOS 50D"); b.ptpString("3-1.0.9"); b.ptpString("1234567")
        val info = DeviceInfo.parse(b.array().copyOf(b.position()))

        assertEquals("Canon EOS 50D", info.model)
        assertEquals("1234567", info.serial)
        assertTrue(info.supports(PtpOp.EOS_REMOTE_RELEASE))
        assertFalse(info.supports(PtpOp.EOS_REMOTE_RELEASE_ON))
    }

    @Test
    fun labelsMatchTheCameraDisplay() {
        assertEquals("f/1.8", EosValues.label(EosProp.APERTURE, 0x15))
        assertEquals("1/125", EosValues.label(EosProp.SHUTTER_SPEED, 0x70))
        assertEquals("400", EosValues.label(EosProp.ISO, 0x58))
        assertEquals("Sombra", EosValues.label(EosProp.WHITE_BALANCE, 8))
        assertEquals("M", EosValues.label(EosProp.SHOOTING_MODE, 3))
    }

    @Test
    fun exposureCompensationIsSignedEighths() {
        assertEquals("0", EosValues.exposureCompensation(0))
        assertEquals("+1/3", EosValues.exposureCompensation(0x03))
        assertEquals("+1 2/3", EosValues.exposureCompensation(0x0D))
        assertEquals("−2/3", EosValues.exposureCompensation(0xFB))
        assertEquals("−2", EosValues.exposureCompensation(0xF0))
        assertEquals("−1", EosValues.exposureCompensation(0xFFFFFFF8.toInt()))
    }
}
