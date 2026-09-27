package com.example.core.ptp

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Scripted camera: each queued chunk is returned by one bulk read, like a USB transfer. */
class FakeTransport(override val readBufferSize: Int = 512) : PtpTransport {
    val written = mutableListOf<ByteArray>()
    val reads = ArrayDeque<ByteArray>()

    override fun write(data: ByteArray, timeoutMs: Int) {
        written += data
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val chunk = reads.removeFirstOrNull() ?: throw IOException("timeout")
        require(chunk.size <= buffer.size) { "chunk larger than read buffer" }
        chunk.copyInto(buffer)
        return chunk.size
    }

    override fun close() {}

    fun queueResponse(code: Int, tid: Int, vararg params: Int) {
        reads += container(PtpContainer.TYPE_RESPONSE, code, tid, params.toBytes())
    }

    /** Queues a data container split into read-sized chunks, the way the device sends it. */
    fun queueData(code: Int, tid: Int, payload: ByteArray, chunk: Int = readBufferSize) {
        val whole = container(PtpContainer.TYPE_DATA, code, tid, payload)
        whole.toList().chunked(chunk).forEach { reads += it.toByteArray() }
        if (whole.size % chunk == 0) reads += ByteArray(0) // zero-length packet terminator
    }

    companion object {
        fun container(type: Int, code: Int, tid: Int, payload: ByteArray): ByteArray =
            ByteBuffer.allocate(12 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(12 + payload.size).putShort(type.toShort()).putShort(code.toShort()).putInt(tid)
                .put(payload).array()

        fun IntArray.toBytes(): ByteArray {
            val b = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
            forEach { b.putInt(it) }
            return b.array()
        }

        fun header(bytes: ByteArray) = PtpContainer.parseHeader(bytes, bytes.size)
    }
}
