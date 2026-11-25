package com.example.core.render

import com.example.core.preset.CurvePoint
import com.example.core.preset.PresetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The numbers the media page gets for its live preview, and the fixtures for the check that the page's
 * WebGL2 preview matches the app: a test picture, a busy preset as JSON and the app's own render of it
 * (build/look-parity). `app/src/test/web/look-parity.js` renders the same in Chromium and compares.
 */
class LookParityFixtureTest {

    /** Most panels at once: tone, curve, colour mixer, grading, calibration, clarity and vignette. */
    private val look = PresetSettings()
        .with("Exposure2012", 0.35).with("Contrast2012", 25.0).with("Highlights2012", -40.0).with("Shadows2012", 30.0)
        .with("Whites2012", 10.0).with("Blacks2012", -15.0).with("IncrementalTemperature", 12.0).with("IncrementalTint", -5.0)
        .with("Vibrance", 20.0).with("Saturation", -10.0).with("HueAdjustmentOrange", -8.0).with("SaturationAdjustmentBlue", -30.0)
        .with("LuminanceAdjustmentGreen", 15.0).with("ColorGradeShadowHue", 220.0).with("ColorGradeShadowSat", 25.0)
        .with("ColorGradeHighlightHue", 40.0).with("ColorGradeHighlightSat", 20.0).with("RedHue", 5.0).with("BlueSaturation", 15.0)
        .with("Clarity2012", 20.0).with("Texture2012", 10.0).with("PostCropVignetteAmount", -25.0)
        .withCurve("ToneCurvePV2012", listOf(CurvePoint(0, 10), CurvePoint(64, 56), CurvePoint(192, 205), CurvePoint(255, 248)))

    private fun picture(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        // Smooth colour ramps, a skin-like patch and hard edges: what clarity and the mixer touch.
        var r = (x * 255 / w); var g = (y * 255 / h); var b = 255 - (x + y) * 255 / (w + h)
        if ((x / 40 + y / 40) % 2 == 0) { r = (r * 3 + 200) / 4; g = (g * 3 + 150) / 4; b = (b * 3 + 120) / 4 }
        if (x in w / 3..w / 2 && y in h / 3..h / 2) { r = 225; g = 170; b = 140 }
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun save(pixels: IntArray, w: Int, h: Int, file: File) {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, pixels, 0, w)
        ImageIO.write(img, "png", file)
    }

    @Test
    fun theJsonCarriesEveryUniformAndTheLutAsBytes() {
        val p = RenderParams.from(look)
        val json = RenderParamsJson.encode(p)
        listOf("uWb", "uExposure", "uHsl", "uGrading", "uCalib", "uMono", "uGray", "uClarity", "uVignette", "uGrainSize", "lut").forEach {
            assertTrue("$it missing", json.contains("\"$it\":"))
        }
        val lut = Regex("\"lut\":\\[([0-9,]*)]").find(json)!!.groupValues[1].split(',').map { it.toInt() }
        assertEquals(768, lut.size)
        assertEquals(RenderParamsJson.lutByte(p.lut[300]), lut[300])
        assertTrue(json.contains("\"uExposure\":" + String.format(java.util.Locale.ROOT, "%.6f", p.exposure).trimEnd('0').trimEnd('.')))
    }

    @Test
    fun writesTheParityFixtures() {
        val w = 640; val h = 400
        val src = picture(w, h)
        val out = File("build/look-parity").apply { mkdirs() }
        save(src, w, h, File(out, "source.png"))
        File(out, "params.json").writeText(RenderParamsJson.encode(RenderParams.from(look)))
        File(out, "identity.json").writeText(RenderParamsJson.encode(RenderParams.IDENTITY))
        save(CpuRenderer.render(src, w, h, RenderParams.from(look)), w, h, File(out, "app.png"))
        assertTrue(File(out, "app.png").length() > 0)
    }
}
