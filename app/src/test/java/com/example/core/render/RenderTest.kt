package com.example.core.render

import com.example.core.preset.CurvePoint
import com.example.core.preset.PresetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RenderTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun lum(c: Int) = 0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)

    /** 16×16 gradient with a green and an orange patch. */
    private val w = 16
    private val h = 16
    private val img = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        when {
            x < 4 && y < 4 -> argb(60, 160, 60)
            x > 11 && y < 4 -> argb(230, 140, 60)
            else -> (x * 16 + y).coerceIn(0, 255).let { argb(it, it, it) }
        }
    }

    private fun render(s: PresetSettings) = CpuRenderer.render(img, w, h, RenderParams.from(s))

    @Test
    fun emptyPresetIsIdentity() {
        val p = RenderParams.from(PresetSettings())
        assertTrue(p.isIdentity)
        assertEquals(img.toList(), CpuRenderer.render(img, w, h, p).toList())
    }

    @Test
    fun zeroValuedSlidersAlsoLeaveThePhotoAlone() {
        val s = PresetSettings(mapOf("Exposure2012" to "0.00", "Contrast2012" to "0", "ColorGradeBlending" to "50", "ProcessVersion" to "11.0"))
        assertTrue(RenderParams.from(s).isIdentity)
    }

    @Test
    fun nonIdentityWithNeutralMathStaysClose() {
        // A tiny exposure change turns the pipeline on; the result must stay within a couple of levels.
        val out = render(PresetSettings().with("Exposure2012", 0.01))
        for (i in img.indices) assertTrue("pixel $i", abs(lum(out[i]) - lum(img[i])) < 3.0)
    }

    @Test
    fun exposureAndHighlightsMoveTheRightWay() {
        val brighter = render(PresetSettings().with("Exposure2012", 1.0))
        assertTrue(lum(brighter[8 * w + 8]) > lum(img[8 * w + 8]))
        val bright = 15 * w + 15
        val tamed = render(PresetSettings().with("Highlights2012", -100.0))
        assertTrue(lum(tamed[bright]) < lum(img[bright]))
        val lifted = render(PresetSettings().with("Shadows2012", 100.0))
        val dark = 1 * w + 5
        assertTrue(lum(lifted[dark]) > lum(img[dark]))
    }

    @Test
    fun colourMixerDesaturatesOnlyGreens() {
        val out = render(PresetSettings().with("SaturationAdjustmentGreen", -100.0))
        val green = out[1 * w + 1]
        val orange = out[1 * w + 13]
        assertTrue("green became grey", abs(r(green) - g(green)) < 25)
        assertTrue("orange kept its colour", r(orange) - b(orange) > 120)
    }

    @Test
    fun blackAndWhiteRemovesColour() {
        val out = render(PresetSettings().withText("ConvertToGrayscale", "True"))
        for (c in out) { assertEquals(r(c), g(c)); assertEquals(g(c), b(c)) }
        assertFalse(RenderParams.from(PresetSettings().withText("ConvertToGrayscale", "True")).isIdentity)
    }

    @Test
    fun warmerTemperatureRaisesRedOverBlue() {
        val out = render(PresetSettings().with("IncrementalTemperature", 60.0))
        val c = out[8 * w + 8]
        assertTrue(r(c) > b(c) + 10)
    }

    @Test
    fun toneCurveIsMonotoneAndHitsItsPoints() {
        val f = CurveMath.spline(listOf(CurvePoint(0, 20), CurvePoint(64, 50), CurvePoint(128, 140), CurvePoint(255, 240)))
        var last = -1f
        for (i in 0..255) { val y = f(i / 255f); assertTrue(y >= last - 1e-6f); last = y }
        assertEquals(20 / 255f, f(0f), 1e-4f)
        assertEquals(140 / 255f, f(128 / 255f), 1e-3f)
        assertEquals(240 / 255f, f(1f), 1e-4f)
    }

    @Test
    fun vignetteDarkensCornersNotCentre() {
        val flat = IntArray(w * h) { argb(128, 128, 128) }
        val out = CpuRenderer.render(flat, w, h, RenderParams.from(PresetSettings().with("PostCropVignetteAmount", -80.0)))
        assertTrue(lum(out[0]) < lum(out[8 * w + 8]))
    }
}
