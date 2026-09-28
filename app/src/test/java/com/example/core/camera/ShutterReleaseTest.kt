package com.example.core.camera

import com.example.core.ptp.FakeTransport
import com.example.core.ptp.PtpException
import com.example.core.ptp.PtpResponse
import com.example.core.ptp.PtpSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShutterReleaseTest {

    @Test
    fun keepsTryingWhileTheCameraFocuses() = runTest {
        val t = FakeTransport()
        t.queueResponse(PtpResponse.OK, 1, ShutterRelease.NO_FOCUS) // "OK", but it couldn't focus yet
        t.queueResponse(PtpResponse.DEVICE_BUSY, 2)
        t.queueResponse(PtpResponse.OK, 3, 0)                       // fired
        val waits = mutableListOf<Long>()
        ShutterRelease.fire(PtpSession(t), remoteRelease = true, waitMs = 5_000) { waits += it }
        assertEquals(3, t.written.size)
        assertEquals(listOf(0L, 300L), waits)
    }

    @Test
    fun givesUpWithTheReasonWhenFocusNeverLocks() = runTest {
        val t = FakeTransport()
        repeat(4) { t.queueResponse(PtpResponse.OK, it + 1, ShutterRelease.NO_FOCUS) }
        try {
            ShutterRelease.fire(PtpSession(t), remoteRelease = true, waitMs = 1_000)
            fail("expected a failure")
        } catch (e: PtpException) {
            assertTrue(e.message!!.contains("focar"))
        }
        assertEquals(4, t.written.size)
    }

    @Test
    fun fullCardStopsAtOnce() = runTest {
        val t = FakeTransport()
        t.queueResponse(PtpResponse.OK, 1, ShutterRelease.CARD_FULL)
        try {
            ShutterRelease.fire(PtpSession(t), remoteRelease = true, waitMs = 5_000)
            fail("expected a failure")
        } catch (e: PtpException) {
            assertTrue(e.message!!.contains("Cartão"))
        }
        assertEquals(1, t.written.size)
    }
}
