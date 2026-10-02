package com.example.core.delivery

import com.example.core.data.Settings
import java.security.SecureRandom

/** The plain decisions of the delivery, kept apart so they can be tested without a phone. */
object DeliveryRules {

    const val PORT = 8080
    /** The phone's address in a Wi-Fi Direct group it owns; Android always uses this one. */
    const val DIRECT_HOST = "192.168.49.1"
    /** 4 digits: quick to say and type. 5 wrong tries a minute per phone keep guessing slow. */
    const val CODE_LENGTH = 4
    /** Clients seen by the portal in this window count as connected. */
    const val ONLINE_MS = 2 * 60_000L

    private val random = SecureRandom()

    /** 128-bit random token, as 32 hex characters; it goes in the client's QR code. */
    fun newToken(): String = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /** [CODE_LENGTH] digits, different from every open session's code. */
    fun newCode(taken: Collection<String>): String {
        while (true) {
            val code = (0 until CODE_LENGTH).joinToString("") { random.nextInt(10).toString() }
            if (code !in taken && code.toSet().size > 1) return code
        }
    }

    /** 10 characters without the ones that look alike (0/O, 1/l/I), easy to type from a printed plate. */
    fun newPassword(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        return (0 until 10).joinToString("") { alphabet[random.nextInt(alphabet.length)].toString() }
    }

    /**
     * Android only accepts a Wi-Fi Direct name of the form DIRECT-xy…: anything typed without it gets
     * the prefix, and characters outside letters, digits and - _ become -.
     */
    fun ssid(typed: String): String {
        val clean = typed.trim().replace(Regex("[^A-Za-z0-9_-]"), "-").trim('-').take(28)
        if (Regex("^DIRECT-[A-Za-z0-9]{2}").containsMatchIn(clean)) return clean
        val rest = clean.removePrefix("DIRECT-").removePrefix("DIRECT").trim('-')
        return "DIRECT-JR" + if (rest.isEmpty()) "-Fotos" else "-$rest"
    }

    /** WPA2 needs 8 to 63 characters. */
    fun passwordOk(password: String): Boolean = password.length in 8..63

    /** Text of a "join this Wi-Fi" QR code; phone cameras (Android and iPhone) read it. */
    fun wifiQr(ssid: String, password: String): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        return "WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};;"
    }

    /** The portal's address; with a [token] the client goes straight to their photos. */
    fun portalUrl(host: String, port: Int = PORT, token: String? = null): String =
        "http://$host" + (if (port == 80) "" else ":$port") + "/" + (token?.let { "?s=$it" } ?: "")

    /** Client name as a file prefix: "Ana Lúcia" → "Ana-Lucia". */
    fun fileSafe(name: String): String {
        val plain = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return plain.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(30).ifEmpty { "Cliente" }
    }

    fun itemName(client: String, savedBase: String): String = "${fileSafe(client)}_$savedBase.jpg"

    /** End of a new session's validity (0 = none). */
    fun expiresAt(now: Long, hours: Int): Long = if (hours <= 0) 0 else now + hours * 3_600_000L

    /**
     * Which session a just-captured photo goes to: none in manual mode; in auto mode the open event
     * gallery when that option is on, else the active client while their session is valid.
     */
    fun autoTarget(settings: Settings, active: DeliverySession?, event: DeliverySession?, now: Long): Long? {
        if (!settings.deliveryEnabled || settings.deliveryMode != "auto") return null
        if (settings.deliveryEventGallery) return event?.takeIf { it.isValid(now) }?.id
        return active?.takeIf { it.isValid(now) && !it.event }?.id
    }
}

/**
 * Wrong codes typed in the portal: after [max] in [windowMs] from the same device, that device waits.
 * Six digits are a million combinations, so 5 a minute takes years to guess.
 */
class AttemptLimiter(private val max: Int = 5, private val windowMs: Long = 60_000L, private val clock: () -> Long = System::currentTimeMillis) {
    private val failures = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun blocked(key: String): Boolean = recent(key).size >= max

    @Synchronized
    fun failed(key: String) {
        recent(key).addLast(clock())
    }

    @Synchronized
    fun reset(key: String) {
        failures.remove(key)
    }

    private fun recent(key: String): ArrayDeque<Long> {
        val list = failures.getOrPut(key) { ArrayDeque() }
        val now = clock()
        while (list.isNotEmpty() && now - list.first() >= windowMs) list.removeFirst()
        return list
    }
}
