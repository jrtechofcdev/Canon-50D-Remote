package com.example.core.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppNumberTest {
    @Test
    fun brazilianFormats() {
        assertEquals("5511999998888", WhatsAppNumber.normalize("(11) 99999-8888"))
        assertEquals("5511999998888", WhatsAppNumber.normalize("011 99999 8888"))
        assertEquals("5511999998888", WhatsAppNumber.normalize("55 11 99999-8888"))
        assertEquals("558133334444", WhatsAppNumber.normalize("81 3333-4444"))
        assertEquals("351912345678", WhatsAppNumber.normalize("+351 912 345 678"))
        assertNull(WhatsAppNumber.normalize("12345"))
    }

    @Test
    fun prettyPrint() {
        assertEquals("+55 (11) 99999-8888", WhatsAppNumber.pretty("5511999998888"))
        assertEquals("+55 (81) 3333-4444", WhatsAppNumber.pretty("558133334444"))
    }

    @Test
    fun recentList() {
        val a = Contact("5511999998888", "Ana")
        val b = Contact("5581999990000", "Léo")
        val list = WhatsAppNumber.remember(WhatsAppNumber.remember(listOf(a), b), a)
        assertEquals(listOf(a, b), list)
        assertEquals(list, WhatsAppNumber.decode(WhatsAppNumber.encode(list)))
    }
}
