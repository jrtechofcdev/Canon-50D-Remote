package com.example.core.delivery

import com.example.core.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryRulesTest {

    @Test
    fun tokensAndCodesAreRandomAndUnique() {
        val tokens = (1..200).map { DeliveryRules.newToken() }.toSet()
        assertEquals(200, tokens.size)
        assertTrue(tokens.all { Regex("[0-9a-f]{32}").matches(it) })
        val taken = (1..50).map { DeliveryRules.newCode(emptyList()) }
        val code = DeliveryRules.newCode(taken)
        assertEquals(4, code.length)
        assertFalse(code in taken)
        assertTrue(DeliveryRules.passwordOk(DeliveryRules.newPassword()))
    }

    @Test
    fun wifiDirectNameAlwaysHasTheAndroidPrefix() {
        assertEquals("DIRECT-JR-Fotos", DeliveryRules.ssid("DIRECT-JR-Fotos"))
        assertEquals("DIRECT-JR-Casamento-Ana", DeliveryRules.ssid("Casamento Ana"))
        assertEquals("DIRECT-JR-Fotos", DeliveryRules.ssid(""))
        assertEquals("DIRECT-ab-Studio", DeliveryRules.ssid("DIRECT-ab-Studio"))
    }

    @Test
    fun wifiQrEscapesSpecialCharacters() {
        assertEquals("WIFI:T:WPA;S:DIRECT-JR-Fotos;P:a\\;b\\:c;;", DeliveryRules.wifiQr("DIRECT-JR-Fotos", "a;b:c"))
        assertEquals("http://192.168.49.1:8080/?s=abc", DeliveryRules.portalUrl("192.168.49.1", 8080, "abc"))
        assertEquals("Ana-Lucia_IMG_0001.jpg", DeliveryRules.itemName("Ana Lúcia", "IMG_0001"))
    }

    @Test
    fun autoModeSendsOnlyToAValidTarget() {
        val now = 1_000_000L
        val active = DeliverySession(id = 3, client = "Ana", code = "123456", token = "t", createdAt = 0, expiresAt = now + 10)
        val event = active.copy(id = 9, event = true)
        val manual = Settings(deliveryEnabled = true, deliveryMode = "manual")
        val auto = manual.copy(deliveryMode = "auto")
        assertNull(DeliveryRules.autoTarget(manual, active, null, now))
        assertNull(DeliveryRules.autoTarget(auto.copy(deliveryEnabled = false), active, null, now))
        assertEquals(3L, DeliveryRules.autoTarget(auto, active, null, now))
        assertNull(DeliveryRules.autoTarget(auto, active.copy(expiresAt = now - 1), null, now))
        assertNull(DeliveryRules.autoTarget(auto, active.copy(closedAt = 5), null, now))
        assertEquals(9L, DeliveryRules.autoTarget(auto.copy(deliveryEventGallery = true), active, event, now))
    }

    @Test
    fun fiveWrongCodesBlockForAMinute() {
        var now = 0L
        val limiter = AttemptLimiter(clock = { now })
        repeat(5) { assertFalse(limiter.blocked("1.2.3.4")); limiter.failed("1.2.3.4") }
        assertTrue(limiter.blocked("1.2.3.4"))
        assertFalse(limiter.blocked("1.2.3.5"))
        now += 60_000
        assertFalse(limiter.blocked("1.2.3.4"))
    }

    @Test
    fun autoModeLeavesOutBadFramesButNeverOneItCouldNotCheck() {
        val good = com.example.core.data.Photo(session = "S", baseName = "A", savedBase = "A", capturedAt = 1, score = 80f)
        assertNull(DeliveryRules.autoQualityProblem(good, skipBlurry = true, skipEyes = true))
        assertEquals("tremida ou fora de foco", DeliveryRules.autoQualityProblem(good.copy(blurry = true), true, false))
        assertNull(DeliveryRules.autoQualityProblem(good.copy(blurry = true), skipBlurry = false, skipEyes = false))
        assertEquals("olhos fechados", DeliveryRules.autoQualityProblem(good.copy(eyesClosed = 1), false, true))
        // Not analysed (failed or too slow): it goes.
        assertNull(DeliveryRules.autoQualityProblem(good.copy(score = null, blurry = true), true, true))
        assertNull(DeliveryRules.autoQualityProblem(null, true, true))
    }
}
