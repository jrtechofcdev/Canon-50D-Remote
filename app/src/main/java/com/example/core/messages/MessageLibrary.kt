package com.example.core.messages

import org.json.JSONArray
import org.json.JSONObject

/**
 * One WhatsApp text. [code] is the short word typed when sending ("ent" finds "entrega"),
 * so with many clients the right message is two letters away.
 */
data class MessageTemplate(val id: String, val name: String, val code: String, val text: String)

/**
 * Every message the photographer keeps: the five of earlier versions (their edited texts carry over)
 * plus ready ones for the rest of a job, from booking to pickup. Stored as JSON in Ajustes.
 */
object MessageLibrary {

    /** The old fixed messages, by the id that replaces each [MessageKind]. */
    val kindIds = mapOf(
        MessageKind.WhatsApp to "fotos",
        MessageKind.Preview to "previa",
        MessageKind.Delivered to "entrega",
        MessageKind.Payment to "pag",
        MessageKind.Selection to "sel",
    )

    private const val SIGN = "\n\n*Jr_Tech_OFC*"

    val extras = listOf(
        MessageTemplate(
            "agenda", "Confirmação do ensaio", "agenda",
            "Olá, {cliente}! 📅\n\nPassando para confirmar nosso ensaio do dia {data}.\n\n" +
                "*Importante:* chegue com 10 minutos de antecedência para aproveitarmos bem a luz." + SIGN,
        ),
        MessageTemplate(
            "local", "Local e horário", "local",
            "Olá, {cliente}! 📍\n\n*Local:* \n*Horário:* \n\nQualquer imprevisto, me avise por aqui." + SIGN,
        ),
        MessageTemplate(
            "sinal", "Reserva da data", "sinal",
            "Olá, {cliente}! ✨\n\nPara reservar a data {data}, o sinal pode ser feito pelo Pix:\n*{pix}*\n\n" +
                "Assim que receber, envio a confirmação." + SIGN,
        ),
        MessageTemplate(
            "pix", "Dados do Pix", "pix",
            "Olá, {cliente}!\n\nSegue a chave Pix para o pagamento:\n*{pix}*\n\nDepois é só me enviar o comprovante por aqui. 🙏" + SIGN,
        ),
        MessageTemplate(
            "orcamento", "Orçamento", "orc",
            "Olá, {cliente}! 📸\n\nObrigado pelo contato. Segue o orçamento:\n\n*Serviço:* \n*Valor:* \n*Entrega:* \n\n" +
                "Qualquer dúvida, estou à disposição." + SIGN,
        ),
        MessageTemplate(
            "atraso", "Edição em andamento", "edit",
            "Olá, {cliente}! 🎨\n\nSuas fotos estão em edição e ficam prontas em breve. " +
                "Assim que finalizar, envio tudo por aqui." + SIGN,
        ),
        MessageTemplate(
            "retirada", "Impressos prontos", "ret",
            "Olá, {cliente}! 🖨️\n\nSeus impressos estão prontos para retirada.\n\n*Horário:* " + SIGN,
        ),
        MessageTemplate(
            "wifi", "Fotos no Wi-Fi (entrega local)", "wifi",
            "Olá, {cliente}! 📸\n\nSuas fotos estão prontas no Wi-Fi do fotógrafo.\n\n" +
                "*1.* Conecte no Wi-Fi *{wifi}* (senha: *{senha}*)\n*2.* Abra *{link}*\n*3.* Digite o código *{codigo}*\n\n" +
                "Se o celular avisar que o Wi-Fi não tem internet, toque em *Manter conectado*." + SIGN,
        ),
        MessageTemplate(
            "obrigado", "Agradecimento", "obg",
            "Olá, {cliente}! 💚\n\nObrigado pela confiança. Foi um prazer fotografar vocês.\n\n" +
                "Se puder, marque *@jr_tech_ofc* quando postar as fotos." + SIGN,
        ),
    )

    /** Ids of the ready messages added after the list became editable (4.0: the local delivery one). */
    private val addedLater = setOf("wifi")

    /** Every template: the saved list, or the defaults built from the old per-kind texts. */
    fun all(saved: String, legacy: (MessageKind) -> String): List<MessageTemplate> {
        decode(saved).takeIf { it.isNotEmpty() }?.let { list ->
            // Messages that arrived in a later version join a list saved before them.
            return list + extras.filter { it.id in addedLater && list.none { saved -> saved.id == it.id } }
        }
        return defaults(legacy)
    }

    fun defaults(legacy: (MessageKind) -> String): List<MessageTemplate> =
        MessageKind.entries.map { kind -> MessageTemplate(kindIds.getValue(kind), kind.label, kindIds.getValue(kind), legacy(kind)) } + extras

    /**
     * Templates matching what was typed: the code first ("ent" → entrega), then the name.
     * Nothing typed lists them all.
     */
    fun search(all: List<MessageTemplate>, query: String): List<MessageTemplate> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        val byCode = all.filter { it.code.lowercase().startsWith(q) }
        val byName = all.filter { it !in byCode && (it.name.lowercase().contains(q) || it.code.lowercase().contains(q)) }
        return byCode + byName
    }

    fun encode(list: List<MessageTemplate>): String = JSONArray(
        list.map { JSONObject().put("id", it.id).put("name", it.name).put("code", it.code).put("text", it.text) },
    ).toString()

    fun decode(text: String): List<MessageTemplate> {
        if (text.isBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { o ->
                val name = o.optString("name").trim()
                if (name.isEmpty()) null
                else MessageTemplate(o.optString("id").ifBlank { name.lowercase() }, name, o.optString("code").trim().lowercase(), o.optString("text"))
            }
        }
    }

    /** A short code from a name, unique in [taken]: "Parabéns aniversário" → "parabens". */
    fun codeFor(name: String, taken: Set<String>): String {
        val base = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]+"), " ").trim().substringBefore(' ').take(10).ifBlank { "msg" }
        if (base !in taken) return base
        var n = 2
        while ("$base$n" in taken) n++
        return "$base$n"
    }
}
