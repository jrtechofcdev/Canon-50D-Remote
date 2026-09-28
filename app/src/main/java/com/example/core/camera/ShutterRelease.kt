package com.example.core.camera

import com.example.core.ptp.PtpException
import com.example.core.ptp.PtpOp
import com.example.core.ptp.PtpResponse
import com.example.core.ptp.PtpResponseException
import com.example.core.ptp.PtpSession
import kotlinx.coroutines.delay

/** What the app's shutter button is doing, for the button to show it. */
sealed interface ShutterState {
    data object Idle : ShutterState
    /** The camera is still focusing (or busy): the app keeps asking until it fires or time runs out. */
    data class Focusing(val waitedMs: Long) : ShutterState
    data object Fired : ShutterState
    data class Failed(val message: String) : ShutterState
}

/**
 * Fires the 50D and waits for it. EOS RemoteRelease answers "OK" even when nothing happened: its
 * first response parameter says why (1 = could not focus, 7 = card full…). The old code took that
 * "OK" as a shot, so a frame the AF hadn't locked yet was simply lost. Here a not-yet-focused or busy
 * camera is asked again until it fires or [waitMs] runs out.
 */
object ShutterRelease {

    const val NO_FOCUS = 1
    const val CARD_FULL = 7
    const val RETRY_MS = 300L

    /** Why the camera didn't fire, from RemoteRelease's first response parameter, or null when it did. */
    fun refusal(params: IntArray): Int? = params.firstOrNull()?.takeIf { it != 0 }

    fun message(code: Int): String = when (code) {
        NO_FOCUS -> "A câmera não conseguiu focar. Aponte para algo com contraste ou use MF."
        CARD_FULL -> "Cartão cheio ou sem cartão na câmera."
        3, 8 -> "A câmera está ocupada gravando. Tente de novo em instantes."
        else -> "A câmera não disparou (código $code)."
    }

    /**
     * Tries until the camera fires. [remoteRelease] is the 50D's one-step release; without it the
     * newer press/release pair is used. [onWaiting] reports how long it has been waiting.
     */
    suspend fun fire(
        ptp: PtpSession,
        remoteRelease: Boolean,
        waitMs: Long,
        onWaiting: (Long) -> Unit = {},
    ) {
        var lastRefusal: Int? = null
        var attempt = 0
        while (true) {
            // Counted in retries, so the wait doesn't depend on how long each USB round trip takes.
            val waited = attempt++ * RETRY_MS
            try {
                val refusal = if (remoteRelease) {
                    refusal(ptp.transact(PtpOp.EOS_REMOTE_RELEASE))
                } else {
                    ptp.transact(PtpOp.EOS_REMOTE_RELEASE_ON, 3, 0)
                    ptp.transact(PtpOp.EOS_REMOTE_RELEASE_OFF, 3)
                    null
                }
                if (refusal == null) return
                if (refusal == CARD_FULL) throw PtpException(message(refusal))
                lastRefusal = refusal
            } catch (e: PtpResponseException) {
                if (e.code != PtpResponse.DEVICE_BUSY && e.code != PtpResponse.NOT_READY) throw e
                lastRefusal = null
            }
            if (waited + RETRY_MS > waitMs) {
                throw PtpException(lastRefusal?.let(::message) ?: "A câmera ficou ocupada por ${waitMs / 1000} s e não disparou.")
            }
            onWaiting(waited)
            delay(RETRY_MS)
        }
    }
}
