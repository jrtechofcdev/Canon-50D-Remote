package com.example.core.payments

import com.example.core.data.Settings
import java.text.Normalizer

/**
 * Pix "copia e cola" (BR Code), the Central Bank's static format: key, receiver, city and, when known,
 * the amount. Any bank app reads it from the QR or from the pasted text. Made on the phone, offline.
 */
object PixCode {

    /** The key as the BR Code wants it: CPF/CNPJ digits, phone with +55, e-mail and random key as typed. */
    fun normalizeKey(key: String, type: String): String {
        val k = key.trim()
        return when (type) {
            "cpf", "cnpj" -> k.filter(Char::isDigit)
            "phone" -> {
                val digits = k.filter(Char::isDigit)
                if (k.startsWith("+")) "+$digits" else if (digits.startsWith("55") && digits.length >= 12) "+$digits" else "+55$digits"
            }
            "email" -> k.lowercase()
            else -> k
        }
    }

    /** Upper case, no accents, only what the BR Code accepts, cut to [max]. */
    fun plain(text: String, max: Int): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .uppercase().replace(Regex("[^A-Z0-9 ]"), "").replace(Regex("\\s+"), " ").trim().take(max)

    fun payload(key: String, name: String, city: String, amountCents: Int?, txid: String = "***"): String {
        fun field(id: String, value: String) = id + value.length.toString().padStart(2, '0') + value
        val account = field("00", "br.gov.bcb.pix") + field("01", key)
        val body = buildString {
            append(field("00", "01"))
            append(field("26", account))
            append(field("52", "0000"))
            append(field("53", "986"))
            if (amountCents != null && amountCents > 0) append(field("54", "%d.%02d".format(amountCents / 100, amountCents % 100)))
            append(field("58", "BR"))
            append(field("59", plain(name, 25).ifEmpty { "RECEBEDOR" }))
            append(field("60", plain(city, 15).ifEmpty { "BRASIL" }))
            append(field("62", field("05", txid)))
            append("6304")
        }
        return body + crc16(body)
    }

    /** CRC16-CCITT (0x1021, start 0xFFFF), as the BR Code asks, 4 hex digits. */
    fun crc16(text: String): String {
        var crc = 0xFFFF
        text.toByteArray(Charsets.UTF_8).forEach { b ->
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return "%04X".format(crc)
    }
}

/**
 * How much the chosen photos cost: the price per photo, or a package (N photos for a price) when it
 * comes out cheaper, or the price for all of them. Always the best deal for the client.
 */
object PricePlan {
    data class Quote(val cents: Int, val detail: String)

    fun quote(s: Settings, count: Int, available: Int): Quote? {
        if (count <= 0) return null
        val per = s.expressPriceCents
        val options = buildList {
            if (per > 0) add(Quote(per * count, "$count × ${money(per)}"))
            val packN = s.payPackPhotos
            val packPrice = s.payPackCents
            if (packN > 1 && packPrice > 0 && count >= packN) {
                val packs = count / packN
                val rest = count % packN
                if (rest == 0 || per > 0) {
                    val cents = packs * packPrice + rest * per
                    add(Quote(cents, "$packs pacote${if (packs > 1) "s" else ""} de $packN" + if (rest > 0) " + $rest × ${money(per)}" else ""))
                }
            }
            if (s.payAllCents > 0 && available > 0 && count >= available) add(Quote(s.payAllCents, "Todas as fotos"))
        }
        return options.minByOrNull { it.cents }
    }

    fun money(cents: Int): String = "R$ %d,%02d".format(cents / 100, cents % 100)

    /** "R$ 15,00 por foto · 5 por R$ 60,00 · todas por R$ 120,00" for the page. */
    fun table(s: Settings): String? = listOfNotNull(
        s.expressPriceCents.takeIf { it > 0 }?.let { "${money(it)} por foto" },
        if (s.payPackPhotos > 1 && s.payPackCents > 0) "${s.payPackPhotos} por ${money(s.payPackCents)}" else null,
        s.payAllCents.takeIf { it > 0 }?.let { "todas por ${money(it)}" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    val methodLabels = linkedMapOf("pix" to "Pix", "cash" to "Dinheiro", "debit" to "Cartão de débito", "credit" to "Cartão de crédito")

    fun methods(s: Settings): List<String> = s.payMethods.split(',').map { it.trim() }.mapNotNull { methodLabels[it] }

    /** The Pix code for [cents] (null amount = the client types it), or null without a key. */
    fun pix(s: Settings, cents: Int?): String? {
        if (s.pixKey.isBlank() || "pix" !in s.payMethods.split(',')) return null
        return PixCode.payload(PixCode.normalizeKey(s.pixKey, s.pixKeyType), s.pixName.ifBlank { s.expressPhotographer }, s.pixCity, cents)
    }
}
