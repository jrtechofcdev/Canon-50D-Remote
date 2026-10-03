package com.example.core.delivery.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MdnsPacketTest {

    /** A query as an iPhone sends it: one question, [type], optionally asking for a unicast answer. */
    private fun query(name: String, type: Int = 1, unicast: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        u16(0); u16(0); u16(1); u16(0); u16(0); u16(0)
        name.split('.').forEach { out.write(it.length); out.write(it.toByteArray()) }
        out.write(0)
        u16(type); u16(if (unicast) 0x8001 else 1)
        return out.toByteArray()
    }

    @Test
    fun answersOnlyItsOwnName() {
        assertNotNull(MdnsPacket.asksFor(query("jrexpress.local"), "jrexpress.local"))
        assertNotNull(MdnsPacket.asksFor(query("JRExpress.local", type = 255), "jrexpress.local"))
        assertTrue(MdnsPacket.asksFor(query("jrexpress.local", unicast = true), "jrexpress.local")!!.unicast)
        assertNull(MdnsPacket.asksFor(query("outro.local"), "jrexpress.local"))
        assertNull(MdnsPacket.asksFor(query("jrexpress.local", type = 28), "jrexpress.local")) // AAAA: no IPv6
        assertNull(MdnsPacket.asksFor(ByteArray(5), "jrexpress.local"))
    }

    @Test
    fun answerCarriesTheAddress() {
        val a = MdnsPacket.answer("jrfotos.local", "192.168.49.1")
        assertEquals(0x84, a[2].toInt() and 0xFF) // response, authoritative
        assertEquals(1, a[7].toInt()) // one answer
        assertEquals(listOf(192, 168, 49, 1), a.takeLast(4).map { it.toInt() and 0xFF })
        // Our own answer is not taken as a question.
        assertNull(MdnsPacket.asksFor(a, "jrfotos.local"))
        assertEquals("jr-fotos", MdnsResponder.hostName("JR Fotos!", "x"))
        assertEquals("express", MdnsResponder.hostName("  ", "express"))
    }
}
