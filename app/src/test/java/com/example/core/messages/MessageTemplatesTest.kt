package com.example.core.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MessageTemplatesTest {
    private val date = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 27, 15, 0) }.timeInMillis

    @Test
    fun fillsEveryField() {
        val text = MessageTemplates.fill(MessageDefaults.SELECTION, MessageData("Ana", 2, date, listOf("IMG_1234", "IMG_1240")))
        assertTrue(text.startsWith("Olá, Ana!"))
        assertTrue(text.contains("*2 fotografias*"))
        assertTrue(text.contains("• IMG_1234\n• IMG_1240"))
        assertFalse(text.contains("{"))
    }

    @Test
    fun whatsAppMessageUsesTheClientName() {
        val text = MessageTemplates.fill(MessageDefaults.WHATSAPP, MessageData("Maria", 3, date))
        assertTrue(text.startsWith("Olá, Maria!"))
        assertTrue(text.contains("*3 fotos*"))
        assertFalse(text.contains("{"))
    }

    @Test
    fun noClientNameStillReadsWell() {
        val text = MessageTemplates.fill(MessageDefaults.WHATSAPP, MessageData("", 30, date))
        assertTrue(text.startsWith("Olá! Tudo bem? 📸"))
        assertTrue(text.contains("27/09/2026"))
        // The new messages open with other words around the name.
        assertTrue(MessageTemplates.fill(MessageCatalog.byId("duvida")!!.text, MessageData("", 0, date)).startsWith("Claro! Será um prazer ajudar."))
        assertTrue(MessageTemplates.fill(MessageCatalog.byId("insatisfacao")!!.text, MessageData("", 0, date)).startsWith("Olá. Sinto muito"))
    }

    @Test
    fun theFieldsTheAppDoesNotKnowAreTypedAndNeverGoOutEmpty() {
        val orc = MessageCatalog.byId("orcamento")!!.text
        val bare = MessageTemplates.fill(orc, MessageData("Ana", 0, date))
        assertEquals(listOf("servico", "valor", "entrega"), MessageTemplates.missing(bare))
        val done = MessageTemplates.fill(orc, MessageData("Ana", 0, date, extra = mapOf("servico" to "Ensaio externo", "valor" to "R$ 350,00", "entrega" to "até 10 dias")))
        assertTrue(done.contains("📷 *Serviço:* Ensaio externo\n💰 *Investimento:* R$ 350,00\n📦 *Prazo de entrega:* até 10 dias"))
        assertTrue(MessageTemplates.missing(done).isEmpty())
        // An empty Pix key stays a field to fill, not a blank after "Chave Pix:".
        assertEquals(listOf("pix"), MessageTemplates.missing(MessageTemplates.fill(MessageCatalog.byId("pix")!!.text, MessageData("Ana", 0, date))))
        assertTrue(MessageTemplates.missing(MessageTemplates.fill(MessageCatalog.byId("pix")!!.text, MessageData("Ana", 0, date, pix = "fotos@jr.com"))).isEmpty())
        // Typed after the text was edited by hand: only the fields still there change.
        assertEquals("Local: Praça\nHora: {horario}", MessageTemplates.applyExtra("Local: {local}\nHora: {horario}", mapOf("local" to "Praça", "horario" to " ")))
        // Something in braces that is not a field is the photographer's own text.
        assertTrue(MessageTemplates.missing("Use {isso} aqui").isEmpty())
    }

    @Test
    fun lightroomListHasNoDuplicates() {
        assertEquals("IMG_1 IMG_2", MessageTemplates.lightroomFilter(listOf("IMG_1", "IMG_2", "IMG_1")))
    }
}
