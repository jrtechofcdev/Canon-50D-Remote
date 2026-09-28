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

class ObjectInfoAndBatteryTest {
    @org.junit.Test
    fun parsesObjectInfoDataset() {
        val b = java.nio.ByteBuffer.allocate(256).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x00010001); b.putShort(PtpFormat.CANON_CR2.toShort()); b.putShort(0)
        b.putInt(15_000_000); b.putShort(PtpFormat.EXIF_JPEG.toShort()); b.putInt(5000)
        b.putInt(160); b.putInt(120); b.putInt(4752); b.putInt(3168); b.putInt(0)
        b.putInt(0x90000000.toInt()); b.putShort(0); b.putInt(0); b.putInt(0)
        fun str(s: String) { b.put((s.length + 1).toByte()); s.forEach { b.putShort(it.code.toShort()) }; b.putShort(0) }
        str("IMG_0042.CR2"); str("20260927T101500")
        val info = ObjectInfo.parse(7, b.array().copyOf(b.position()))
        org.junit.Assert.assertEquals("IMG_0042.CR2", info.filename)
        org.junit.Assert.assertEquals(15_000_000L, info.size)
        org.junit.Assert.assertEquals("20260927T101500", info.captureDate)
        org.junit.Assert.assertTrue(info.isRaw)
        org.junit.Assert.assertEquals(7, info.asObjectAdded().handle)
    }

    @org.junit.Test
    fun batteryLevels() {
        org.junit.Assert.assertEquals("100%", EosValues.batteryLabel(2))
        org.junit.Assert.assertTrue(EosValues.batteryIsLow(0))
        org.junit.Assert.assertTrue(EosValues.batteryIsLow(5))
        org.junit.Assert.assertEquals(false, EosValues.batteryIsLow(4))
    }
}

class CameraClockTest {
    @org.junit.Test
    fun clockIsLocalWallTime() {
        val brt = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
        // 2026-09-28 12:00:00 UTC is 09:00:00 in Brasília; the camera stores 09:00 as if it were UTC.
        val utcNoon = 1_790_596_800_000L
        org.junit.Assert.assertEquals(1_790_586_000, com.example.core.ptp.EosValues.cameraClock(utcNoon, brt))
        val packet = com.example.core.ptp.PtpContainer.eosPropValue(com.example.core.ptp.EosProp.CAMERA_TIME, 1_790_586_000)
        val buf = java.nio.ByteBuffer.wrap(packet).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        org.junit.Assert.assertEquals(12, buf.int)
        org.junit.Assert.assertEquals(0xD113, buf.int)
        org.junit.Assert.assertEquals(1_790_586_000, buf.int)
    }
}
