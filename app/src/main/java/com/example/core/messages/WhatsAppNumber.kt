package com.example.core.messages

/** A client's phone number, and the recent ones kept for quick sending. */
data class Contact(val number: String, val name: String = "")

object WhatsAppNumber {

    /**
     * Digits WhatsApp expects (country code + area code + number). Brazilian numbers typed the usual
     * way, "(11) 99999-9999", "011 99999 9999" or "11999999999", get the 55 in front.
     * Returns null when it cannot be a phone number.
     */
    fun normalize(input: String): String? {
        var digits = input.filter(Char::isDigit)
        if (input.trim().startsWith("+")) return digits.takeIf { it.length in 10..15 }
        digits = digits.trimStart('0')
        return when (digits.length) {
            10, 11 -> "55$digits"                          // DDD + number
            12, 13 -> digits.takeIf { it.startsWith("55") } ?: digits
            in 14..15 -> digits
            else -> null
        }
    }

    /** "+55 (11) 99999-9999" for display. */
    fun pretty(normalized: String): String {
        if (!normalized.startsWith("55") || normalized.length !in 12..13) return "+$normalized"
        val ddd = normalized.substring(2, 4)
        val rest = normalized.substring(4)
        val split = rest.length - 4
        return "+55 ($ddd) ${rest.substring(0, split)}-${rest.substring(split)}"
    }

    /** WhatsApp's chat id for a number, used to open that chat with the photos already attached. */
    fun jid(normalized: String) = "$normalized@s.whatsapp.net"

    fun encode(list: List<Contact>): String = list.joinToString(";") { "${it.number}|${it.name.replace(Regex("[|;]"), " ")}" }

    fun decode(text: String): List<Contact> = text.split(';').mapNotNull { entry ->
        val parts = entry.split('|')
        parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Contact(it, parts.getOrElse(1) { "" }) }
    }

    /** Puts [contact] first, without duplicates, keeping the last [max]. */
    fun remember(list: List<Contact>, contact: Contact, max: Int = 8): List<Contact> =
        (listOf(contact) + list.filter { it.number != contact.number }).take(max)
}
