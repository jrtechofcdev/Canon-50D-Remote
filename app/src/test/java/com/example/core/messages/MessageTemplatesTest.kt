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
        assertTrue(text.contains("*2 fotos*"))
        assertTrue(text.contains("• IMG_1234\n• IMG_1240"))
        assertFalse(text.contains("{"))
    }

    @Test
    fun noClientNameStillReadsWell() {
        val text = MessageTemplates.fill(MessageDefaults.PREVIEW, MessageData("", 30, date))
        assertTrue(text.startsWith("Olá! 📸"))
        assertTrue(text.contains("27/09/2026"))
    }

    @Test
    fun lightroomListHasNoDuplicates() {
        assertEquals("IMG_1 IMG_2", MessageTemplates.lightroomFilter(listOf("IMG_1", "IMG_2", "IMG_1")))
    }
}
