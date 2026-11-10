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
    fun savedListRoundTripsAndPixIsFilled() {
        val list = listOf(MessageTemplate("m1", "Parabéns", "parabens", "Oi {cliente}, Pix: {pix}"))
        val back = MessageLibrary.decode(MessageLibrary.encode(list))
        assertEquals(list, back)
        val text = MessageTemplates.fill(back.first().text, MessageData("Ana", 3, 0, pix = "fotos@jr.com"))
        assertEquals("Oi Ana, Pix: fotos@jr.com", text)
        assertEquals("parabens2", MessageLibrary.codeFor("Parabéns atrasado", setOf("parabens")))
    }
}
