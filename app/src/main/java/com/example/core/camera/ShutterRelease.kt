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

    /** How long the camera may answer only "busy" before [fire] calls its recovery once. */
    const val STUCK_MS = 1_500L

    /**
     * Tries until the camera fires. [remoteRelease] is the 50D's one-step release; without it the
     * newer press/release pair is used. [onWaiting] reports how long it has been waiting.
     *
     * The wait is the longer of the retries counted and the real time spent: each RemoteRelease that
     * hunts for focus can take half a second on the camera, and a log showed "up to 5 s" lasting 10 s.
     * When the camera answers only "busy" for [STUCK_MS], [onStuck] runs once (the app closes a
     * leftover AF or live view there). [report] gets one line with every answer, for the log.
     */
    suspend fun fire(
        ptp: PtpSession,
        remoteRelease: Boolean,
        waitMs: Long,
        onStuck: (suspend () -> Unit)? = null,
        report: (String) -> Unit = {},
        onWaiting: (Long) -> Unit = {},
    ) {
        val started = System.currentTimeMillis()
        val answers = Answers()
        var lastRefusal: Int? = null
        var busySince: Long? = null
        var unstuck = false
        var attempt = 0
        while (true) {
            val waited = maxOf(attempt++ * RETRY_MS, System.currentTimeMillis() - started)
            try {
                val refusal = if (remoteRelease) {
                    refusal(ptp.transact(PtpOp.EOS_REMOTE_RELEASE))
                } else {
                    ptp.transact(PtpOp.EOS_REMOTE_RELEASE_ON, 3, 0)
                    ptp.transact(PtpOp.EOS_REMOTE_RELEASE_OFF, 3)
                    null
                }
                answers.add(refusal?.let { if (it == NO_FOCUS) "sem foco" else "recusou $it" } ?: "disparou")
                if (refusal == null) {
                    report(answers.line(System.currentTimeMillis() - started))
                    return
                }
                lastRefusal = refusal
                busySince = null
                if (refusal == CARD_FULL) {
                    report(answers.line(System.currentTimeMillis() - started))
                    throw PtpException(message(refusal))
                }
            } catch (e: PtpResponseException) {
                if (e.code != PtpResponse.DEVICE_BUSY && e.code != PtpResponse.NOT_READY) {
                    answers.add("0x%04X".format(e.code))
                    report(answers.line(System.currentTimeMillis() - started))
                    throw e
                }
                answers.add(if (e.code == PtpResponse.DEVICE_BUSY) "ocupada (0x2019)" else "não pronta (0xA102)")
                lastRefusal = null
                val since = busySince ?: waited.also { busySince = it }
                if (!unstuck && onStuck != null && waited - since >= STUCK_MS) {
                    unstuck = true
                    answers.add("recuperação", attempt = false)
                    onStuck()
                }
            }
            if (waited + RETRY_MS > waitMs) {
                report(answers.line(System.currentTimeMillis() - started))
                throw PtpException(lastRefusal?.let(::message) ?: "A câmera ficou ocupada por ${waitMs / 1000} s e não disparou.")
            }
            onWaiting(waited)
            delay(RETRY_MS)
        }
    }

    /** Counts the camera's answers in order of first appearance: "ocupada (0x2019) ×12, disparou". */
    private class Answers {
        private val counts = LinkedHashMap<String, Int>()
        private var total = 0
        fun add(answer: String, attempt: Boolean = true) {
            counts[answer] = (counts[answer] ?: 0) + 1
            if (attempt) total++
        }
        fun line(ms: Long) = "$total tentativa(s) em $ms ms: " +
            counts.entries.joinToString(", ") { (k, n) -> if (n > 1) "$k ×$n" else k }
    }
}
