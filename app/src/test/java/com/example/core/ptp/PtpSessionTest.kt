package com.example.core.ptp

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PtpSessionTest {

    @Test
    fun openSessionUsesTransactionZeroThenCountsFromOne() = runTest {
        val t = FakeTransport()
        val s = PtpSession(t)
        t.queueResponse(PtpResponse.OK, 0)
        s.openSession()
        t.queueResponse(PtpResponse.OK, 1)
        s.transact(PtpOp.EOS_SET_REMOTE_MODE, 1)

        val open = FakeTransport.header(t.written[0])
        assertEquals(PtpOp.OPEN_SESSION, open.code)
        assertEquals(0, open.transactionId)
        val remote = FakeTransport.header(t.written[1])
        assertEquals(PtpOp.EOS_SET_REMOTE_MODE, remote.code)
        assertEquals(1, remote.transactionId)
        assertEquals(16L, remote.length)
    }

    @Test
    fun readsDataSpanningManyTransfersThenTheResponse() = runTest {
        val t = FakeTransport(readBufferSize = 512)
        val s = PtpSession(t)
        val file = ByteArray(5000) { (it % 251).toByte() }
        t.queueData(PtpOp.GET_OBJECT, 1, file)
        t.queueResponse(PtpResponse.OK, 1)

        val sink = ByteArraySink()
        s.transact(PtpOp.GET_OBJECT, 42, dataIn = sink)

        assertArrayEquals(file, sink.bytes())
        assertTrue("every queued packet consumed", t.reads.isEmpty())
    }

    @Test
    fun skipsZeroLengthPacketWhenDataEndsOnPacketBoundary() = runTest {
        val t = FakeTransport(readBufferSize = 512)
        val s = PtpSession(t)
        val payload = ByteArray(1024 - 12) { 7 }
        t.queueData(PtpOp.GET_OBJECT, 1, payload) // exactly 2 packets + ZLP
        t.queueResponse(PtpResponse.OK, 1)

        val sink = ByteArraySink()
        s.transact(PtpOp.GET_OBJECT, 1, dataIn = sink)
        assertEquals(payload.size, sink.bytes().size)
    }

    @Test
    fun sendsDataPhaseForSetProperty() = runTest {
        val t = FakeTransport()
        val s = PtpSession(t)
        t.queueResponse(PtpResponse.OK, 1)
        s.transact(PtpOp.EOS_SET_DEVICE_PROP_VALUE_EX, dataOut = PtpContainer.eosPropValue(EosProp.ISO, 0x58))

        assertEquals(2, t.written.size)
        val data = t.written[1]
        assertEquals(PtpContainer.TYPE_DATA, FakeTransport.header(data).type)
        assertEquals(12, PtpDataReader.u32At(data, 12))
        assertEquals(EosProp.ISO, PtpDataReader.u32At(data, 16))
        assertEquals(0x58, PtpDataReader.u32At(data, 20))
    }

    @Test
    fun busyResponseDoesNotBreakTheSession() = runTest {
        val t = FakeTransport()
        val s = PtpSession(t)
        t.queueResponse(PtpResponse.DEVICE_BUSY, 1)
        try {
            s.transact(PtpOp.EOS_REMOTE_RELEASE)
            fail("expected busy")
        } catch (e: PtpResponseException) {
            assertEquals(PtpResponse.DEVICE_BUSY, e.code)
        }
        assertFalse(s.isBroken)
        t.queueResponse(PtpResponse.OK, 2)
        s.transact(PtpOp.EOS_REMOTE_RELEASE)
    }

    @Test
    fun staleResponseFromEarlierTransactionIsSkipped() = runTest {
        val t = FakeTransport()
        val s = PtpSession(t)
        t.queueResponse(PtpResponse.OK, 1)
        s.transact(PtpOp.EOS_KEEP_DEVICE_ON)
        t.queueResponse(PtpResponse.OK, 1) // duplicate/late
        t.queueResponse(PtpResponse.OK, 2, 99)
        val params = s.transact(PtpOp.EOS_KEEP_DEVICE_ON)
        assertArrayEquals(intArrayOf(99), params)
    }

    @Test
    fun timeoutMarksSessionBroken() = runTest {
        val t = FakeTransport()
        val s = PtpSession(t)
        try {
            s.transact(PtpOp.EOS_GET_EVENT)
            fail("expected timeout")
        } catch (e: PtpException) {
            assertTrue(s.isBroken)
        }
    }
}
