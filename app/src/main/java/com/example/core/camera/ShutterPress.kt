package com.example.core.camera

import com.example.core.ptp.DeviceInfo
import com.example.core.ptp.PtpOp
import com.example.core.ptp.PtpResponse
import com.example.core.ptp.PtpResponseException
import com.example.core.ptp.PtpSession
import kotlinx.coroutines.delay

/** How the app presses the camera's shutter button over USB. */
enum class ReleaseMethod(val label: String) {
    /** RemoteReleaseOn/Off (0x9128/0x9129): half press, full press, let go, like a finger on the button. */
    PRESS_RELEASE("meio curso + completo (0x9128/0x9129)"),
    /** RemoteRelease (0x910F): focus and fire in one command, asked again while the camera hunts. */
    LEGACY("disparo direto (0x910F)"),
    NONE("sem disparo pelo cabo"),
}

/** The finger on the app's shutter, after it touched down (the touch itself is the half press). */
enum class Gesture { UP, HOLD, CANCEL }

/** How a two-step shot ended. */
enum class PressOutcome { FIRED, NO_PHOTO, CANCELLED }

/**
 * Modo desenvolvedor: [method] "auto" picks what the camera announces; "press" and "legacy" force one
 * (to compare on the camera). [flag] is RemoteReleaseOn's second parameter, which Canon does not document.
 */
data class ReleasePrefs(val method: String = AUTO, val flag: Int = ShutterPress.FLAG) {
    companion object {
        const val AUTO = "auto"
        const val PRESS = "press"
        const val LEGACY = "legacy"
    }
}

/**
 * The shutter button pressed in two steps, the way a finger does it: half press (the camera focuses once),
 * full press (it fires as soon as focus locks, in One-Shot AF), then both let go. Releasing is always
 * sent, whatever happened before: a button left "pressed" kept the 50D answering busy to the next shots.
 * Whether a photo was taken is told by the camera's new-file event, not by the "OK" to the press.
 */
object ShutterPress {
    const val HALF = 1
    const val FULL = 2
    /** RemoteReleaseOn's second parameter; 1 is what a release known to work on the 50D sends. */
    const val FLAG = 1
    private const val RETRY_MS = 100L
    private const val PRESS_TIMEOUT_MS = 3_000L
    /** A finger held this long on the shutter (half press) without lifting is let go: nothing is pressed forever. */
    const val MAX_HALF_HOLD_MS = 60_000L

    /** Drive modes that shoot while the button is held (continuous high/low and their silent variants). */
    private val CONTINUOUS = setOf(0x01, 0x04, 0x05, 0x12, 0x14)

    fun isContinuous(drive: Int?): Boolean = drive in CONTINUOUS

    /** Extra wait for the self-timer: the photo comes 10 s (or 2 s) after the press. */
    fun timerExtraMs(drive: Int?): Long = when (drive) {
        0x10, 0x07 -> 12_000L
        0x11 -> 4_000L
        else -> 0L
    }

    /** Which release this camera gets: the two-step pair when it announces it, the one-step command otherwise. */
    fun methodFor(info: DeviceInfo, choice: String = ReleasePrefs.AUTO): ReleaseMethod = when (choice) {
        ReleasePrefs.PRESS -> ReleaseMethod.PRESS_RELEASE
        ReleasePrefs.LEGACY -> ReleaseMethod.LEGACY
        else -> when {
            info.supports(PtpOp.EOS_REMOTE_RELEASE_ON) && info.supports(PtpOp.EOS_REMOTE_RELEASE_OFF) -> ReleaseMethod.PRESS_RELEASE
            info.supports(PtpOp.EOS_REMOTE_RELEASE) -> ReleaseMethod.LEGACY
            else -> ReleaseMethod.NONE
        }
    }

    /** One line for the log: what the camera announces and what the app will use. */
    fun describe(info: DeviceInfo, method: ReleaseMethod): String {
        fun has(op: Int) = if (info.supports(op)) "sim" else "não"
        return "0x910F ${has(PtpOp.EOS_REMOTE_RELEASE)} · 0x9128 ${has(PtpOp.EOS_REMOTE_RELEASE_ON)} · " +
            "0x9129 ${has(PtpOp.EOS_REMOTE_RELEASE_OFF)} → ${method.label}"
    }

    /** Presses one level, asking again while the camera answers busy or not ready. */
    suspend fun press(ptp: PtpSession, level: Int, flag: Int, answers: MutableList<String>? = null, timeoutMs: Long = PRESS_TIMEOUT_MS) {
        val started = System.currentTimeMillis()
        var tries = 0
        while (true) {
            try {
                ptp.transact(PtpOp.EOS_REMOTE_RELEASE_ON, level, flag)
                answers?.add(if (level == HALF) "meio curso" else "completo")
                return
            } catch (e: PtpResponseException) {
                val retry = e.code == PtpResponse.DEVICE_BUSY || e.code == PtpResponse.NOT_READY
                answers?.add("%s 0x%04X".format(if (level == HALF) "meio curso" else "completo", e.code))
                // Counted in tries too, so the limit holds even when each answer comes back instantly.
                if (!retry || maxOf(++tries * RETRY_MS, System.currentTimeMillis() - started) > timeoutMs) throw e
                delay(RETRY_MS)
            }
        }
    }

    /** Lets go of the full press, then the half press. Never throws: it runs on every way out. */
    suspend fun releaseAll(ptp: PtpSession, answers: MutableList<String>? = null) {
        runCatching { ptp.transact(PtpOp.EOS_REMOTE_RELEASE_OFF, FULL, timeoutMs = 2_000) }
        runCatching { ptp.transact(PtpOp.EOS_REMOTE_RELEASE_OFF, HALF, timeoutMs = 2_000) }
        answers?.add("soltou")
    }

    /**
     * One shot. [nextGesture] is the finger on the app's button (null = a tap already over: half and full
     * press at once). [photoArrived] waits up to the given time for the camera's new-file event; it must be
     * listening before this starts. In a continuous drive mode the button is let go right after the full
     * press (or when the finger lifts, after a hold), so a tap takes one frame; otherwise it stays pressed
     * until the photo arrives, so the camera can finish focusing and fire as a held finger would.
     */
    suspend fun shoot(
        ptp: PtpSession,
        flag: Int,
        continuous: Boolean,
        waitMs: Long,
        photoArrived: suspend (Long) -> Boolean,
        nextGesture: (suspend () -> Gesture)? = null,
        onHalfPressed: () -> Unit = {},
        report: (String) -> Unit = {},
    ): PressOutcome {
        val answers = mutableListOf<String>()
        val started = System.currentTimeMillis()
        var heldBurst = false
        var arrived = false
        try {
            press(ptp, HALF, flag, answers)
            onHalfPressed()
            if (nextGesture != null) {
                // The finger is still down: the camera focuses now, the shot waits for the finger.
                while (true) {
                    when (nextGesture()) {
                        Gesture.CANCEL -> return PressOutcome.CANCELLED.also { answers.add("cancelado") }
                        Gesture.UP -> break
                        Gesture.HOLD -> if (continuous) {
                            press(ptp, FULL, flag, answers)
                            heldBurst = true
                            // A burst while the finger stays down; it ends when the finger lifts or slides off.
                            while (nextGesture() == Gesture.HOLD) Unit
                            break
                        }
                    }
                }
            }
            if (!heldBurst) press(ptp, FULL, flag, answers)
            if (!heldBurst && !continuous) arrived = photoArrived(waitMs)
        } finally {
            releaseAll(ptp, answers)
            report(answers.joinToString(", ") + " em ${System.currentTimeMillis() - started} ms")
        }
        if (heldBurst || continuous) arrived = photoArrived(waitMs)
        return if (arrived) PressOutcome.FIRED else PressOutcome.NO_PHOTO
    }

    /** The message when the camera was pressed but no photo came. */
    fun noPhotoMessage(waitMs: Long) =
        "A câmera não fez a foto em ${waitMs / 1000} s. Aponte para algo com contraste (ou use MF) e confira o cartão."

    /** True when the camera turned the two-step press down as an unknown command. */
    fun unsupported(e: Throwable) = e is PtpResponseException && e.code == PtpResponse.OPERATION_NOT_SUPPORTED

    /** The message for a camera that refuses the two-step press. */
    const val UNSUPPORTED_MESSAGE = "A câmera não aceita o disparo em duas etapas (0x9128). Volte o método para Automático."
}
