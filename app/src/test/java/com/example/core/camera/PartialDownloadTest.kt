package com.example.core.camera

import com.example.core.ptp.ByteArraySink
import com.example.core.ptp.FakeTransport
import com.example.core.ptp.PtpDataSink
import com.example.core.ptp.PtpException
import com.example.core.ptp.PtpOp
import com.example.core.ptp.PtpResponse
import com.example.core.ptp.PtpSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PartialDownloadTest {

    private val file = ByteArray(2_500) { (it * 7).toByte() }

    /** The camera answering block requests from [file]: one data container and one OK per block. */
    private fun camera(t: FakeTransport, from: Long, chunk: Int, firstTid: Int = 1) {
        var tid = firstTid
        var offset = from.toInt()
        while (offset < file.size) {
            val end = minOf(offset + chunk, file.size)
            t.queueData(PtpOp.GET_PARTIAL_OBJECT, tid, file.copyOfRange(offset, end))
            t.queueResponse(PtpResponse.OK, tid, end - offset)
            tid++
            offset = end
        }
    }

    /** (offset, length) of every GetPartialObject the app sent. */
    private fun requests(t: FakeTransport) = t.written.map { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }
        .filter { (it.getShort(6).toInt() and 0xFFFF) == PtpOp.GET_PARTIAL_OBJECT }
        .map { it.getInt(16) to it.getInt(20) }

    /** Remembers every begin() so the test can check progress isn't reset by each block. */
    private class Recording : PtpDataSink {
        val bytes = ByteArraySink()
        val begins = mutableListOf<Long>()
        override fun begin(totalBytes: Long) { begins += totalBytes }
        override fun write(buffer: ByteArray, offset: Int, length: Int) = bytes.write(buffer, offset, length)
    }

    @Test
    fun theFileComesInBlocksAndWhole() = runTest {
        val t = FakeTransport()
        camera(t, 0, chunk = 1_000)
        val sink = Recording()
        var pauses = 0
        PartialDownload.run(PtpSession(t), handle = 7, size = file.size.toLong(), sink = sink, chunk = 1_000, beforeChunk = { pauses++ })
        assertArrayEquals(file, sink.bytes.bytes())
        assertEquals(listOf(0 to 1_000, 1_000 to 1_000, 2_000 to 500), requests(t))
        // One begin with the whole size: the progress bar runs to 100 % once, not once per block.
        assertEquals(listOf(2_500L), sink.begins)
        // The app can wait before each block (the shutter goes first).
        assertEquals(3, pauses)
    }

    @Test
    fun aBrokenFileContinuesFromWhereItStopped() = runTest {
        val t = FakeTransport()
        camera(t, 1_200, chunk = 1_000)
        val sink = Recording()
        PartialDownload.run(PtpSession(t), 7, file.size.toLong(), sink, startAt = 1_200, chunk = 1_000)
        assertArrayEquals(file.copyOfRange(1_200, file.size), sink.bytes.bytes())
        assertEquals(listOf(1_200 to 1_000, 2_200 to 300), requests(t))
    }

    @Test
    fun aBusyCameraIsAskedForTheSameBlockAgain() = runTest {
        val t = FakeTransport()
        t.queueResponse(PtpResponse.DEVICE_BUSY, 1)
        camera(t, 0, chunk = 2_500, firstTid = 2)
        val sink = Recording()
        PartialDownload.run(PtpSession(t), 7, file.size.toLong(), sink, chunk = 2_500)
        assertArrayEquals(file, sink.bytes.bytes())
        assertEquals(listOf(0 to 2_500, 0 to 2_500), requests(t))
    }

    @Test
    fun anEmptyBlockStopsInsteadOfLoopingForever() = runTest {
        val t = FakeTransport()
        t.queueData(PtpOp.GET_PARTIAL_OBJECT, 1, ByteArray(0))
        t.queueResponse(PtpResponse.OK, 1, 0)
        try {
            PartialDownload.run(PtpSession(t), 7, file.size.toLong(), Recording(), chunk = 1_000)
            fail("expected an error")
        } catch (e: PtpException) {
            assertEquals(true, e.message!!.contains("0 de 2500"))
        }
    }

    @Test
    fun capabilitiesComeFromTheOperationList() {
        val info = com.example.core.ptp.DeviceInfo("Canon Inc.", "Canon EOS 50D", "", "", setOf(PtpOp.EOS_GET_VIEWFINDER_DATA, PtpOp.GET_PARTIAL_OBJECT))
        val caps = CameraCapabilities.of(info)
        assertEquals(true, caps.liveView)
        assertEquals(true, caps.partialObject)
        assertEquals(false, caps.af)
        assertEquals(false, caps.dof)
        assertEquals(true, caps.describe().startsWith("sem AF (0x9154)"))
        assertEquals("todas as operações que o app usa", CameraCapabilities().describe())
    }
}
