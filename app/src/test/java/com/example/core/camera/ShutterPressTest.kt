package com.example.core.camera

import com.example.core.ptp.DeviceInfo
import com.example.core.ptp.FakeTransport
import com.example.core.ptp.PtpOp
import com.example.core.ptp.PtpResponse
import com.example.core.ptp.PtpResponseException
import com.example.core.ptp.PtpSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShutterPressTest {

    /** The commands the app sent, as "9128(1,1)", in order. */
    private fun sent(t: FakeTransport): List<String> = t.written.map { bytes ->
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val length = b.getInt(0)
        val code = b.getShort(6).toInt() and 0xFFFF
        val params = (12 until length step 4).map { b.getInt(it) }
        "%04X(%s)".format(code, params.joinToString(","))
    }

    /** A camera that answers OK to the next [n] commands. */
    private fun okFor(t: FakeTransport, n: Int, from: Int = 1) = (from until from + n).forEach { t.queueResponse(PtpResponse.OK, it) }

    private fun info(vararg ops: Int) = DeviceInfo("Canon Inc.", "Canon EOS 50D", "3-1.0.9", "1", ops.toSet())

    @Test
    fun theTwoStepPressIsPreferredWhenTheCameraAnnouncesIt() {
        val both = info(PtpOp.EOS_REMOTE_RELEASE, PtpOp.EOS_REMOTE_RELEASE_ON, PtpOp.EOS_REMOTE_RELEASE_OFF)
        assertEquals(ReleaseMethod.PRESS_RELEASE, ShutterPress.methodFor(both))
        assertEquals(ReleaseMethod.LEGACY, ShutterPress.methodFor(info(PtpOp.EOS_REMOTE_RELEASE)))
        // Only half of the pair is not enough.
        assertEquals(ReleaseMethod.LEGACY, ShutterPress.methodFor(info(PtpOp.EOS_REMOTE_RELEASE, PtpOp.EOS_REMOTE_RELEASE_ON)))
        assertEquals(ReleaseMethod.NONE, ShutterPress.methodFor(info()))
        // Modo desenvolvedor can force either one.
        assertEquals(ReleaseMethod.LEGACY, ShutterPress.methodFor(both, ReleasePrefs.LEGACY))
        assertEquals(ReleaseMethod.PRESS_RELEASE, ShutterPress.methodFor(info(PtpOp.EOS_REMOTE_RELEASE), ReleasePrefs.PRESS))
    }

    @Test
    fun aTapPressesHalfThenFullAndLetsGoOnlyAfterThePhoto() = runTest {
        val t = FakeTransport()
        okFor(t, 4)
        var sentWhenAsked = 0
        val outcome = ShutterPress.shoot(PtpSession(t), ShutterPress.FLAG, continuous = false, waitMs = 5_000, photoArrived = {
            sentWhenAsked = t.written.size
            true
        })
        assertEquals(PressOutcome.FIRED, outcome)
        assertEquals(listOf("9128(1,1)", "9128(2,1)", "9129(2)", "9129(1)"), sent(t))
        // Still pressed while waiting for the photo, the way a finger holds the button until it fires.
        assertEquals(2, sentWhenAsked)
    }

    @Test
    fun theSecondParameterComesFromTheSetting() = runTest {
        val t = FakeTransport()
        okFor(t, 4)
        ShutterPress.shoot(PtpSession(t), flag = 0, continuous = false, waitMs = 5_000, photoArrived = { true })
        assertEquals(listOf("9128(1,0)", "9128(2,0)", "9129(2)", "9129(1)"), sent(t))
    }

    @Test
    fun noPhotoInTimeIsReportedAndTheButtonIsStillLetGo() = runTest {
        val t = FakeTransport()
        okFor(t, 4)
        val outcome = ShutterPress.shoot(PtpSession(t), 1, continuous = false, waitMs = 5_000, photoArrived = { false })
        assertEquals(PressOutcome.NO_PHOTO, outcome)
        assertEquals("9129(1)", sent(t).last())
    }

    @Test
    fun aBusyCameraIsAskedAgainForTheSameLevel() = runTest {
        val t = FakeTransport()
        t.queueResponse(PtpResponse.DEVICE_BUSY, 1)
        okFor(t, 4, from = 2)
        ShutterPress.shoot(PtpSession(t), 1, continuous = false, waitMs = 5_000, photoArrived = { true })
        assertEquals(listOf("9128(1,1)", "9128(1,1)", "9128(2,1)", "9129(2)", "9129(1)"), sent(t))
    }

    @Test
    fun aRefusedFullPressStillLetsGoOfTheButton() = runTest {
        val t = FakeTransport()
        t.queueResponse(PtpResponse.OK, 1)
        t.queueResponse(PtpResponse.GENERAL_ERROR, 2)
        okFor(t, 2, from = 3)
        try {
            ShutterPress.shoot(PtpSession(t), 1, continuous = false, waitMs = 5_000, photoArrived = { true })
            fail("expected the refusal")
        } catch (e: PtpResponseException) {
            assertEquals(PtpResponse.GENERAL_ERROR, e.code)
        }
        // Without these the 50D kept a virtual finger on the button and answered busy to the next shots.
        assertEquals(listOf("9128(1,1)", "9128(2,1)", "9129(2)", "9129(1)"), sent(t))
    }

    @Test
    fun aFingerSlidingOffCancelsWithoutAPhoto() = runTest {
        val t = FakeTransport()
        okFor(t, 3)
        var asked = false
        val outcome = ShutterPress.shoot(
            PtpSession(t), 1, continuous = false, waitMs = 5_000,
            photoArrived = { asked = true; true },
            nextGesture = { Gesture.CANCEL },
        )
        assertEquals(PressOutcome.CANCELLED, outcome)
        assertEquals(listOf("9128(1,1)", "9129(2)", "9129(1)"), sent(t))
        assertTrue("no photo is waited for after a cancel", !asked)
    }

    @Test
    fun holdingInSingleShotWaitsForTheFingerAndTakesOnePhoto() = runTest {
        val t = FakeTransport()
        okFor(t, 4)
        val gestures = ArrayDeque(listOf(Gesture.HOLD, Gesture.UP))
        val outcome = ShutterPress.shoot(
            PtpSession(t), 1, continuous = false, waitMs = 5_000,
            photoArrived = { true },
            nextGesture = { gestures.removeFirst() },
        )
        assertEquals(PressOutcome.FIRED, outcome)
        assertEquals(listOf("9128(1,1)", "9128(2,1)", "9129(2)", "9129(1)"), sent(t))
    }

    @Test
    fun holdingInContinuousShootsUntilTheFingerLifts() = runTest {
        val t = FakeTransport()
        okFor(t, 4)
        val gestures = ArrayDeque(listOf(Gesture.HOLD, Gesture.HOLD, Gesture.UP))
        var sentWhenAsked = 0
        val outcome = ShutterPress.shoot(
            PtpSession(t), 1, continuous = true, waitMs = 5_000,
            photoArrived = { sentWhenAsked = t.written.size; true },
            nextGesture = { gestures.removeFirst() },
        )
        assertEquals(PressOutcome.FIRED, outcome)
        assertEquals(listOf("9128(1,1)", "9128(2,1)", "9129(2)", "9129(1)"), sent(t))
        // In a burst the button is let go first, and then the photos are counted.
        assertEquals(4, sentWhenAsked)
        assertTrue(gestures.isEmpty())
    }

    @Test
    fun driveModesDecideTheHoldAndTheTimerWait() {
        assertTrue(ShutterPress.isContinuous(0x01))
        assertTrue(!ShutterPress.isContinuous(0x00))
        assertTrue(!ShutterPress.isContinuous(null))
        assertEquals(12_000L, ShutterPress.timerExtraMs(0x10))
        assertEquals(0L, ShutterPress.timerExtraMs(0x00))
    }
}
