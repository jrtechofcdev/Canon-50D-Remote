package com.example.core.live

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiveViewParserTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    private fun record(type: Int, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN).putInt(8 + payload.size).putInt(type).put(payload).array()

    @Test
    fun jpegRecordAfterMetadata() {
        // A metadata record (zoom rectangle, histogram…) before the picture, as the camera sends it.
        val data = record(4, ByteArray(20) { 7 }) + record(LiveViewParser.TYPE_JPEG, jpeg)
        assertArrayEquals(jpeg, LiveViewParser.jpeg(data))
    }

    @Test
    fun bareJpegFallback() {
        assertArrayEquals(jpeg, LiveViewParser.jpeg(byteArrayOf(0, 0, 0) + jpeg + byteArrayOf(0, 0)))
    }

    @Test
    fun noPicture() {
        assertNull(LiveViewParser.jpeg(record(4, ByteArray(12))))
    }
}
