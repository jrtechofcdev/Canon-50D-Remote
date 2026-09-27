package com.example.core.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Cr2PreviewTest {

    /** Minimal CR2-like TIFF: IFD0 with StripOffsets/StripByteCounts pointing at a JPEG blob. */
    private fun fakeCr2(jpeg: ByteArray, jpegOffset: Int): ByteArray {
        val file = ByteBuffer.allocate(jpegOffset + jpeg.size + 16).order(ByteOrder.LITTLE_ENDIAN)
        file.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(0x10)
        file.putInt(0x0000_52_43) // "CR" marker area, ignored
        file.putInt(0)
        file.position(0x10)
        file.putShort(2)
        file.putShort(0x0111).putShort(4).putInt(1).putInt(jpegOffset)
        file.putShort(0x0117).putShort(4).putInt(1).putInt(jpeg.size)
        file.putInt(0)
        file.position(jpegOffset)
        file.put(jpeg)
        return file.array()
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun extractsEmbeddedJpegInsideFirstChunk() {
        val out = Cr2Preview.extract(ByteArrayInputStream(fakeCr2(jpeg, 0x100)))
        assertArrayEquals(jpeg, out)
    }

    @Test
    fun extractsEmbeddedJpegBeyondFirstChunk() {
        val out = Cr2Preview.extract(ByteArrayInputStream(fakeCr2(jpeg, 200_000)))
        assertArrayEquals(jpeg, out)
    }

    @Test
    fun returnsNullForNonTiffData() {
        assertNull(Cr2Preview.extract(ByteArrayInputStream(ByteArray(100) { 1 })))
    }
}
