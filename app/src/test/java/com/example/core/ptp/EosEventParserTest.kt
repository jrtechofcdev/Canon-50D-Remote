package com.example.core.ptp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EosEventParserTest {

    private fun objectAdded(handle: Int, format: Int, size: Int, name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.US_ASCII) + 0
        val total = 0x28 + nameBytes.size
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(total); putInt(EosEvent.OBJECT_ADDED_EX)
            putInt(handle); putInt(0x00010001)
            putShort(format.toShort()); putShort(0); putInt(0); putInt(0)
            putInt(size); putInt(0x90000000.toInt()); putInt(0)
            put(nameBytes)
        }.array()
    }

    private fun record(type: Int, vararg fields: Int): ByteArray {
        val b = ByteBuffer.allocate(8 + fields.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(8 + fields.size * 4).putInt(type)
        fields.forEach { b.putInt(it) }
        return b.array()
    }

    private val terminator = record(0)

    @Test
    fun parsesNewFilesPropertyChangesAndAvailableLists() {
        val data = objectAdded(0x1234, PtpFormat.CANON_CR2, 15_000_000, "IMG_0042.CR2") +
            objectAdded(0x1235, PtpFormat.EXIF_JPEG, 4_000_000, "IMG_0042.JPG") +
            record(EosEvent.PROP_VALUE_CHANGED, EosProp.ISO, 0x58) +
            record(EosEvent.AVAIL_LIST_CHANGED, EosProp.APERTURE, 3, 3, 0x20, 0x28, 0x30) +
            terminator

        val records = EosEventParser.parse(data)
        assertEquals(4, records.size)

        val raw = records[0] as EosEventRecord.ObjectAdded
        assertEquals(0x1234, raw.handle)
        assertEquals("IMG_0042.CR2", raw.filename)
        assertEquals(15_000_000L, raw.size)
        assertTrue(raw.isRaw)

        val jpg = records[1] as EosEventRecord.ObjectAdded
        assertTrue(jpg.isJpeg)

        assertEquals(EosEventRecord.PropertyChanged(EosProp.ISO, 0x58), records[2])
        assertEquals(EosEventRecord.AvailableValues(EosProp.APERTURE, listOf(0x20, 0x28, 0x30)), records[3])
    }

    @Test
    fun emptyEventListIsJustTheTerminator() {
        assertTrue(EosEventParser.parse(terminator).isEmpty())
    }

    @Test
    fun truncatedRecordStopsParsingInsteadOfCrashing() {
        val data = record(EosEvent.PROP_VALUE_CHANGED, EosProp.ISO, 0x58).copyOf(10)
        assertTrue(EosEventParser.parse(data).isEmpty())
    }
}
