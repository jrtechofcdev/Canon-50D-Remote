package com.example.core.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageLibraryTest {

    @Test
    fun oldEditedTextsCarryOverAndExtrasArePresent() {
        val all = MessageLibrary.all("") { kind -> if (kind == MessageKind.Delivered) "Meu texto de entrega" else "padrão" }
        assertEquals("Meu texto de entrega", all.first { it.id == "entrega" }.text)
        assertTrue(all.any { it.code == "pix" })
        assertTrue(all.size >= 13)
    }

    @Test
    fun codeFindsTheMessageFirstThenTheName() {
        val all = MessageLibrary.defaults { "x" }
        assertEquals("entrega", MessageLibrary.search(all, "ent").first().id)
        assertEquals("pix", MessageLibrary.search(all, "PIX").first().id)
        assertTrue(MessageLibrary.search(all, "impressos").any { it.id == "retirada" })
        assertEquals(all.size, MessageLibrary.search(all, " ").size)
    }

    @Test
    fun aSavedListTakesTheNewTextsOnlyWhereThePhotographerDidNotWrite() {
        // As saved by 5.7: the old ready texts, one of them rewritten, one deleted, and a message of his own on "prazo".
        val old = MessageLibrary.previous
        val saved = listOf(
            MessageTemplate("fotos", "Envio das fotos", "fotos", old.getValue("fotos")),
            MessageTemplate("pix", "Dados do Pix", "pix", "Minha chave é {pix}, obrigado!"),
            MessageTemplate("agenda", "Confirmação do ensaio", "agenda", old.getValue("agenda")),
            MessageTemplate("m9", "Prazo do álbum", "prazo", "Seu álbum fica pronto em 30 dias."),
        )
        val up = MessageLibrary.upgrade(saved)
        val byId = up.list.associateBy { it.id }
        assertEquals(MessageCatalog.byId("fotos")!!.text, byId.getValue("fotos").text)
        assertEquals(MessageCatalog.byId("agenda")!!.text, byId.getValue("agenda").text)
        assertEquals("Minha chave é {pix}, obrigado!", byId.getValue("pix").text)
        assertEquals("Seu álbum fica pronto em 30 dias.", byId.getValue("m9").text)
        assertEquals("prazo", byId.getValue("m9").code)
        // The 21 new ones joined; the one whose code was his got another code, and that is said.
        assertEquals("prazo2", byId.getValue("prazo").code)
        assertEquals(listOf("Prazo de entrega" to "prazo2"), up.renamed)
        assertTrue(listOf("oi", "cancel", "disponivel", "retirado").all { it in byId })
        // Messages deleted before (local, sinal…) come back once with the update, as the whole catalogue does.
        assertEquals(4 + 32, up.list.size)
        // Running it again changes nothing.
        assertEquals(up.list, MessageLibrary.upgrade(up.list).list)
        // The old per-kind setting still with its default text goes to the new one; a written one stays.
        assertEquals(MessageDefaults.PREVIEW, MessageLibrary.upgradeLegacy(MessageKind.Preview, old.getValue("previa")))
        assertEquals(null, MessageLibrary.upgradeLegacy(MessageKind.Preview, "Meu texto"))
    }

    @Test
    fun savedListRoundTripsAndPixIsFilled() {
        val list = listOf(MessageTemplate("m1", "Parabéns", "parabens", "Oi {cliente}, Pix: {pix}"))
        val back = MessageLibrary.decode(MessageLibrary.encode(list))
        assertEquals(list, back)
        val text = MessageTemplates.fill(back.first().text, MessageData("Ana", 3, 0, pix = "fotos@jr.com"))
        assertEquals("Oi Ana, Pix: fotos@jr.com", text)
        assertEquals("parabens2", MessageLibrary.codeFor("Parabéns atrasado", setOf("parabens")))
    }
}
