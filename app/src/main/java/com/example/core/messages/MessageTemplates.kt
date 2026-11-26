package com.example.core.messages

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Default texts of the five first messages (each kept in its own setting before the library existed).
 * They come from [MessageCatalog]; editable in Ajustes → Mensagens; *asterisks* are WhatsApp bold.
 */
object MessageDefaults {
    val WHATSAPP = MessageCatalog.byId("fotos")!!.text
    val PREVIEW = MessageCatalog.byId("previa")!!.text
    val DELIVERED = MessageCatalog.byId("entrega")!!.text
    val PAYMENT = MessageCatalog.byId("pag")!!.text
    val SELECTION = MessageCatalog.byId("sel")!!.text
}

enum class MessageKind(val label: String) {
    WhatsApp("Envio das fotos"),
    Preview("Prévia pronta"),
    Delivered("Fotos entregues"),
    Payment("Lembrete de pagamento"),
    Selection("Seleção recebida"),
}

/** Values that fill a template's {fields}. */
data class MessageData(
    val client: String,
    val count: Int,
    val date: Long,
    val names: List<String> = emptyList(),
    /** The photographer's Pix key (Ajustes → Mensagens), for {pix}. */
    val pix: String = "",
    /** Entrega local: the client's code, the Wi-Fi name and password, and the portal address. */
    val code: String = "",
    val wifi: String = "",
    val wifiPassword: String = "",
    val link: String = "",
    /** Values typed before sending, for {local}, {horario}, {servico}, {valor}, {entrega}, {prazo} and any other field. */
    val extra: Map<String, String> = emptyMap(),
)

object MessageTemplates {

    /**
     * The fields of the messages, with the name shown when one is still to be filled. The first nine
     * are filled by the app; the rest are typed before sending.
     */
    val FIELDS = linkedMapOf(
        "cliente" to "Nome do cliente", "data" to "Data", "quantidade" to "Quantidade", "lista" to "Lista de fotos",
        "pix" to "Chave Pix", "wifi" to "Nome do Wi-Fi", "senha" to "Senha do Wi-Fi", "link" to "Endereço da galeria", "codigo" to "Código de acesso",
        "local" to "Local", "horario" to "Horário", "servico" to "Serviço", "valor" to "Valor",
        "entrega" to "Prazo de entrega (orçamento)", "prazo" to "Previsão de entrega",
    )

    /** [text] with each typed value in place of its {campo}; blank values leave the field to be pointed out. */
    fun applyExtra(text: String, extra: Map<String, String>): String {
        var out = text
        extra.forEach { (k, v) -> if (v.isNotBlank()) out = out.replace("{$k}", v.trim()) }
        return out
    }

    /** The fields still written as {campo} in [text]: what has to be typed (or the line removed) before sending. */
    fun missing(text: String): List<String> =
        Regex("\\{([a-z]+)\\}").findAll(text).map { it.groupValues[1] }.filter { it in FIELDS }.distinct().toList()

    /**
     * Replaces {cliente}, {quantidade}, {data}, {lista}, {pix}, the delivery's {codigo}, {wifi}, {senha},
     * {link}, and whatever was typed in [MessageData.extra]. A field with no value stays as {campo}, so
     * [missing] can point it out: an empty Pix key or Wi-Fi never reaches the client as a blank.
     */
    fun fill(template: String, data: MessageData, locale: Locale = Locale("pt", "BR")): String {
        val date = SimpleDateFormat("dd/MM/yyyy", locale).format(Date(data.date))
        val list = data.names.joinToString("\n") { "• $it" }
        var out = template
        data.extra.forEach { (k, v) -> if (v.isNotBlank()) out = out.replace("{$k}", v.trim()) }
        fun String.put(key: String, value: String) = if (value.isBlank()) this else replace("{$key}", value)
        return out
            .replace("{cliente}", data.client.trim())
            .replace("{quantidade}", data.count.toString())
            .replace("{data}", date)
            .replace("{lista}", list)
            .put("pix", data.pix.trim())
            .put("codigo", data.code)
            .put("wifi", data.wifi)
            .put("senha", data.wifiPassword)
            .put("link", data.link)
            // "Olá, !" when there is no client name reads wrong; drop the comma instead.
            .replace("Olá, !", "Olá!")
            .replace("Olá, ,", "Olá,")
            .replace("Olá, .", "Olá.")
            .replace("Claro, !", "Claro!")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** File names separated by spaces, for Lightroom Classic's text filter set to "Contém qualquer" (Contains Any). */
    fun lightroomFilter(names: List<String>): String = names.distinct().joinToString(" ")
}
