package com.example.core.ptp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoFormatTest {

    @Test
    fun videosAreRecognisedByFormatOrName() {
        assertTrue(PtpFormat.isVideo(PtpFormat.CANON_MOV, "MVI_0001.MOV"))
        assertTrue(PtpFormat.isVideo(0, "MVI_0001.MOV"))
        assertTrue(PtpFormat.isVideo(0, "M20-1234.MLV"))
        assertTrue(PtpFormat.isVideo(0, "LV_20261120_101500.mp4"))
        assertTrue(PtpFormat.isVideo(PtpFormat.QUICKTIME, "ANY.BIN"))
        assertFalse(PtpFormat.isVideo(PtpFormat.CANON_CR2, "IMG_0001.CR2"))
        assertFalse(PtpFormat.isVideo(PtpFormat.EXIF_JPEG, "IMG_0001.JPG"))
        assertFalse(PtpFormat.isVideo(0, "notes.raw"))
    }

    @Test
    fun objectRecordsKnowTheyAreVideos() {
        val obj = EosEventRecord.ObjectAdded(1, 0x10001, 0x3000, 50_000_000, 0, "MVI_0042.MOV")
        assertTrue(obj.isVideo)
        assertFalse(obj.isRaw)
        assertFalse(obj.isJpeg)
        assertTrue(ObjectInfo(1, 0x10001, 0x3000, 50_000_000, 0, "MVI_0042.MOV", "").isVideo)
    }

    @Test
    fun describeListsEveryRecordIncludingUnknownOnes() {
        fun record(type: Int, vararg fields: Int): ByteArray {
            val b = ByteBuffer.allocate(8 + fields.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(8 + fields.size * 4).putInt(type)
            fields.forEach { b.putInt(it) }
            return b.array()
        }
        val data = record(EosEvent.PROP_VALUE_CHANGED, EosProp.EVF_RECORD_STATUS, 4) + record(0xC1FF, 7) + record(0)
        val lines = EosEventParser.describe(data)
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("0xC189(16): prop 0xD1B8 = 4"))
        assertTrue(lines[1], lines[1].startsWith("0xC1FF(12):"))
    }
}
