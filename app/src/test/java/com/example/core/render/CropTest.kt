package com.example.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class CropTest {
    private val w = 3000f
    private val h = 2000f

    @Test
    fun roundTrip() {
        val spec = CropSpec(0.1f, 0.2f, 0.8f, 0.9f, -3.25f, 1, flipH = true, ratio = CropRatio.IG_PORTRAIT.id, targetW = 1080, targetH = 1350)
        val back = CropSpec.decode(spec.encode())!!
        assertEquals(spec.copy(angle = -3.25f), back.copy(angle = -3.25f))
        assertEquals(-3.25f, back.angle, 0.001f)
        assertTrue(CropSpec().isIdentity)
    }

    @Test
    fun straightenedFullFrameDoesNotFit() {
        assertTrue(CropGeometry.inside(CropSpec(), w, h))
        assertFalse(CropGeometry.inside(CropSpec(angle = 10f), w, h))
        val fitted = CropGeometry.largest(CropSpec(angle = 10f), w, h, null)
        assertTrue(CropGeometry.inside(fitted, w, h))
        // Straightening 10° on a 3:2 frame costs roughly a quarter of each side, not more.
        assertTrue(fitted.width in 0.6f..0.85f)
    }

    @Test
    fun aspectPresetKeepsItsShape() {
        val aspect = CropGeometry.aspect(CropRatio.IG_PORTRAIT, w, h, landscape = false)!!
        assertEquals(0.8f, aspect, 1e-4f)
        val spec = CropGeometry.largest(CropSpec(), w, h, aspect)
        assertEquals(0.8f, spec.width * w / (spec.height * h), 1e-3f)
        assertEquals(1f, spec.height, 1e-3f) // a portrait crop of a landscape photo uses the full height
        // Stories flipped to landscape becomes 16:9.
        assertEquals(16f / 9f, CropGeometry.aspect(CropRatio.STORY, w, h, landscape = true)!!, 1e-4f)
    }

    @Test
    fun dragStopsAtTheEdge() {
        val start = CropSpec(0.2f, 0.2f, 0.6f, 0.6f)
        val pushed = start.copy(left = start.left - 0.5f, right = start.right - 0.5f)
        val result = CropGeometry.towards(start, pushed, w, h)
        assertEquals(0f, result.left, 0.01f)
        assertEquals(0.4f, result.width, 1e-4f)
    }

    @Test
    fun horizonFound() {
        // Sky over sea, horizon tilted +5° (going down to the right).
        val gw = 400
        val gh = 300
        val t = tan(Math.toRadians(5.0))
        val gray = IntArray(gw * gh) { i ->
            val x = i % gw
            val y = i / gw
            val horizon = gh / 2 + (x - gw / 2) * t
            // Anti-aliased like a real photo: the pixel on the line gets a mix of sky and sea.
            val sea = (y + 0.5 - horizon).coerceIn(0.0, 1.0)
            (200 - 140 * sea).toInt()
        }
        val correction = HorizonDetector.detect(gray, gw, gh)
        assertNotNull(correction)
        assertEquals(-5f, correction!!, 0.5f)
        // A flat grey picture has no lines to go by.
        assertEquals(null, HorizonDetector.detect(IntArray(gw * gh) { 128 }, gw, gh))
    }
}
