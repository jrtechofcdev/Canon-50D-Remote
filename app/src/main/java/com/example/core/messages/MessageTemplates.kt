package com.example.core.messages

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Default WhatsApp texts. Editable in Ajustes → Mensagens; *asterisks* are WhatsApp bold. */
object MessageDefaults {
    const val WHATSAPP = "Olá, {cliente}! 📸\n\n" +
        "Aqui estão suas fotos: *{quantidade}* do dia {data}.\n\n" +
        "Qualquer dúvida, estou à disposição.\n\n" +
        "*Jr_Tech_OFC*"
    const val PREVIEW = "Olá, {cliente}! 📸\n\n" +
        "Suas fotos já estão prontas para escolher: *{quantidade} fotos* do dia {data}.\n\n" +
        "Me envie os números das que você mais gostou.\n\n" +
        "*Jr_Tech_OFC*"
    const val DELIVERED = "Olá, {cliente}! ✨\n\n" +
        "Suas *{quantidade} fotos* editadas estão entregues. Foi um prazer fotografar vocês.\n\n" +
        "Se puder, marque *@jr_tech_ofc* quando postar.\n\n" +
        "*Jr_Tech_OFC*"
    const val PAYMENT = "Olá, {cliente}! Tudo bem?\n\n" +
        "*Lembrete:* o restante do pagamento do ensaio de {data} fica para a entrega das fotos.\n\n" +
        "Qualquer dúvida, estou à disposição.\n\n" +
        "*Jr_Tech_OFC*"
    const val SELECTION = "Olá, {cliente}! 💚\n\n" +
        "Recebi sua seleção: *{quantidade} fotos*.\n\n" +
        "{lista}\n\n" +
        "Agora sigo com a edição e aviso quando estiver pronto.\n\n" +
        "*Jr_Tech_OFC*"
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
)

object MessageTemplates {

    /** Replaces {cliente}, {quantidade}, {data} and {lista}; unknown fields are left as typed. */
    fun fill(template: String, data: MessageData, locale: Locale = Locale("pt", "BR")): String {
        val date = SimpleDateFormat("dd/MM/yyyy", locale).format(Date(data.date))
        val list = data.names.joinToString("\n") { "• $it" }
        return template
            .replace("{cliente}", data.client.trim())
            .replace("{quantidade}", data.count.toString())
            .replace("{data}", date)
            .replace("{lista}", list)
            // "Olá, !" when there is no client name reads wrong; drop the comma instead.
            .replace("Olá, !", "Olá!")
            .replace("Olá, ,", "Olá,")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** File names separated by spaces, for Lightroom Classic's text filter set to "Contém qualquer" (Contains Any). */
    fun lightroomFilter(names: List<String>): String = names.distinct().joinToString(" ")
}
