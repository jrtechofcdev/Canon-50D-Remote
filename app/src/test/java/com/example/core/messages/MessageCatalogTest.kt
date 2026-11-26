package com.example.core.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ready messages are the document's, character for character (Central de Atendimento v1.3, section 5). */
class MessageCatalogTest {

    /** (name, code, text) of every message in the document, read the way a person reads it. */
    private fun document(): List<Triple<String, String, String>> {
        val lines = javaClass.getResourceAsStream("/messages/atendimento_v1.3.md")!!.bufferedReader(Charsets.UTF_8).readLines()
        val out = mutableListOf<Triple<String, String, String>>()
        var i = 0
        while (i < lines.size) {
            val title = Regex("^#### \\S+\\. (.+)$").find(lines[i])
            if (title != null) {
                var j = i + 1
                var code = ""
                while (!lines[j].startsWith("```text")) {
                    Regex("^\\*\\*Atalho:\\*\\* `([^`]+)`").find(lines[j])?.let { code = it.groupValues[1] }
                    j++
                }
                var k = j + 1
                val body = mutableListOf<String>()
                while (lines[k] != "```") body += lines[k++]
                out += Triple(title.groupValues[1].trim(), code, body.joinToString("\n"))
                i = k
            }
            i++
        }
        return out
    }

    @Test
    fun everyMessageIsTheDocumentsTextUnchanged() {
        val doc = document()
        assertEquals(35, doc.size)
        assertEquals(35, MessageCatalog.ALL.size)
        doc.forEachIndexed { i, (name, code, text) ->
            val m = MessageCatalog.ALL[i]
            assertEquals(name, m.name)
            assertEquals(code, m.code)
            assertEquals("texto de \"$name\"", text, m.text)
        }
    }

    @Test
    fun theFourteenKeepTheirIdsAndCodesAndTheNewOnesJoin() {
        val old = mapOf(
            "fotos" to "fotos", "previa" to "previa", "entrega" to "entrega", "pag" to "pag", "sel" to "sel", "agenda" to "agenda",
            "local" to "local", "sinal" to "sinal", "pix" to "pix", "orcamento" to "orc", "atraso" to "edit", "retirada" to "ret",
            "wifi" to "wifi", "obrigado" to "obg",
        )
        old.forEach { (id, code) -> assertEquals(code, MessageCatalog.byId(id)!!.code) }
        val codes = MessageCatalog.ALL.map { it.code }
        assertEquals(35, codes.toSet().size)
        listOf("oi", "duvida", "disp", "disponivel", "indisponivel", "pago", "preparo", "lembrete", "reag", "cancel", "pendente", "prazo",
            "suporte", "avaliacao", "autoriza", "retorno", "indicacao", "imprevisto", "insatisfacao", "andamento", "retirado")
            .forEach { assertTrue(it, it in codes) }
        // The defaults of the app are these texts: 5 kinds + 30 more.
        val defaults = MessageLibrary.defaults { kind -> MessageCatalog.byId(MessageLibrary.kindIds.getValue(kind))!!.text }
        assertEquals(MessageCatalog.ALL.map { it.id }.toSet(), defaults.map { it.id }.toSet())
        assertEquals(MessageDefaults.WHATSAPP, MessageCatalog.byId("fotos")!!.text)
    }

    @Test
    fun everyReadyMessageHasItsStepAndTheirFieldsAreKnown() {
        MessageCatalog.ALL.forEach { m ->
            assertTrue(m.code, MessageLibrary.categoryOf(m) != MessageLibrary.Category.MINE)
            Regex("\\{([a-z]+)\\}").findAll(m.text).forEach { f -> assertTrue("${m.code}: ${f.value}", f.groupValues[1] in MessageTemplates.FIELDS) }
        }
        assertEquals(MessageLibrary.Category.MINE, MessageLibrary.categoryOf(MessageTemplate("m1", "Parabéns", "parabens", "")))
        // A ready message whose code the photographer changed stays in its step.
        assertEquals(MessageLibrary.Category.PAYMENT, MessageLibrary.categoryOf(MessageCatalog.byId("pix")!!.copy(code = "chave")))
    }
}
