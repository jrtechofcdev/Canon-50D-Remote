package com.example.core.payments

import com.example.core.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentsTest {

    @Test
    fun crcMatchesTheCentralBankExample() {
        // The static BR Code example published by the Banco Central do Brasil.
        val body = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304"
        assertEquals("1D3D", PixCode.crc16(body))
    }

    @Test
    fun pixCodeCarriesKeyAmountAndAValidCrc() {
        val code = PixCode.payload("jr.tech.ofc@gmail.com", "José Renato", "Caruaru", 4500)
        assertTrue(code.contains("0014br.gov.bcb.pix0121jr.tech.ofc@gmail.com"))
        assertTrue(code.contains("540545.00"))
        assertTrue(code.contains("5911JOSE RENATO"))
        assertTrue(code.contains("6007CARUARU"))
        assertEquals(PixCode.crc16(code.dropLast(4)), code.takeLast(4))
        assertEquals("+5581999990000", PixCode.normalizeKey("(81) 99999-0000", "phone"))
        assertEquals("12345678901", PixCode.normalizeKey("123.456.789-01", "cpf"))
    }

    @Test
    fun keysTheBrCodeCantCarryAreFlagged() {
        assertNull(PixCode.keyProblem("", "email"))
        assertNull(PixCode.keyProblem("jr.tech.ofc@gmail.com", "email"))
        assertEquals("Confira o e-mail", PixCode.keyProblem("jr.tech.ofc", "email"))
        assertTrue(PixCode.keyProblem("joão@gmail.com", "email")!!.contains("acento"))
        assertTrue(PixCode.keyProblem("a".repeat(70) + "@gmail.com", "email")!!.contains("longa"))
        assertNull(PixCode.keyProblem("123.456.789-01", "cpf"))
        assertEquals("O CPF precisa de 11 números", PixCode.keyProblem("123.456.789", "cpf"))
        assertNull(PixCode.keyProblem("123e4567-e12b-12d1-a456-426655440000", "random"))
        // A trunk 0 before the area code is dropped.
        assertEquals("+5581999990000", PixCode.normalizeKey("081 99999-0000", "phone"))
        assertNull(PixCode.keyProblem("081 99999-0000", "phone"))
        // No Pix code is made from a key the bank would refuse.
        val s = Settings(pixKey = "jr.tech.ofc", pixKeyType = "email", payMethods = "pix")
        assertNull(PricePlan.pix(s, 100))
    }

    @Test
    fun amountsUseDotsWhateverThePhoneLanguage() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            val code = PixCode.payload("x@y.com", "A", "B", 1234)
            assertTrue(code, code.contains("540512.34"))
            assertEquals(PixCode.crc16(code.dropLast(4)), code.takeLast(4))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun theClientAlwaysGetsTheCheapestPrice() {
        val s = Settings(expressPriceCents = 1500, payPackPhotos = 5, payPackCents = 6000, payAllCents = 10000)
        assertEquals(4500, PricePlan.quote(s, 3, 20)!!.cents)
        assertEquals(6000, PricePlan.quote(s, 5, 20)!!.cents) // package beats 5 × 15
        assertEquals(7500, PricePlan.quote(s, 6, 20)!!.cents) // package + 1
        assertEquals(10000, PricePlan.quote(s, 8, 8)!!.cents) // all photos beats 1 package + 3
        assertNull(PricePlan.quote(s, 0, 8))
        assertNull(PricePlan.quote(Settings(), 3, 8))
        assertEquals("R$ 15,00 por foto · 5 por R$ 60,00 · todas por R$ 100,00", PricePlan.table(s))
        assertNull(PricePlan.pix(s.copy(pixKey = "x@y.com", payMethods = "cash"), 100))
    }
}
