package com.example.core.render

import com.example.core.preset.CurvePoint
import com.example.core.preset.PresetSettings
import kotlin.math.max
import kotlin.math.min

/**
 * Everything the renderer needs, normalised from Adobe's slider ranges (mostly -100..100) to the
 * small floats the shader uses. Built once per settings change; sliders only swap these numbers.
 *
 * The maths approximates Lightroom's look; Adobe's processing is not public. The preset itself keeps
 * the exact Adobe values, so Lightroom renders the exported XMP exactly.
 */
class RenderParams(
    /** WB multipliers for R and B, and G for tint (linear light). */
    val wbR: Float, val wbG: Float, val wbB: Float,
    val exposure: Float,
    val contrast: Float, val highlights: Float, val shadows: Float, val whites: Float, val blacks: Float,
    val vibrance: Float, val saturation: Float,
    /** 8 bands × (hue shift in turns, saturation factor delta, luminance delta). */
    val hsl: FloatArray,
    /** Shadows, midtones, highlights, global: (r, g, b tint offset, luminance delta) each. */
    val grading: FloatArray,
    val gradingBalance: Float, val gradingBlending: Float,
    /** Red, green, blue primaries: (hue shift, saturation delta); then shadow tint. */
    val calibration: FloatArray, val shadowTint: Float,
    val monochrome: Boolean,
    /** 8 bands of the B&W mixer (luminance deltas). */
    val grayMixer: FloatArray,
    val texture: Float, val clarity: Float, val dehaze: Float,
    val sharpen: Float, val sharpenMasking: Float, val noiseReduction: Float,
    val vignette: Float, val vignetteMidpoint: Float, val vignetteFeather: Float, val vignetteRoundness: Float,
    val grain: Float, val grainSize: Float,
    /** 256 × RGB, the tone curves (parametric → point RGB → per channel) folded into one table. */
    val lut: FloatArray,
    /** True when the settings change nothing, so the photo can be shown as-is. */
    val isIdentity: Boolean = false,
) {

    companion object {
        val IDENTITY: RenderParams by lazy { from(PresetSettings()) }

        fun from(s: PresetSettings): RenderParams {
            fun n(key: String, d: Double = 0.0) = s.num(key, d).toFloat()

            // White balance. JPEGs use incremental values; a raw preset's Kelvin is taken relative to daylight.
            var temp = n("IncrementalTemperature")
            var tint = n("IncrementalTint")
            if (s.values["WhiteBalance"] == "Custom" && s.values.containsKey("Temperature")) {
                temp = ((n("Temperature") - 5500f) / 40f).coerceIn(-100f, 100f)
                tint = n("Tint").coerceIn(-100f, 100f)
            }
            val wbR = 1f + temp / 100f * 0.32f
            val wbB = 1f - temp / 100f * 0.32f
            val wbG = 1f - tint / 100f * 0.22f

            val hsl = FloatArray(24)
            PresetSettings.HSL_COLORS.forEachIndexed { i, c ->
                hsl[i * 3] = n("HueAdjustment$c") / 100f * (30f / 360f)
                hsl[i * 3 + 1] = n("SaturationAdjustment$c") / 100f
                hsl[i * 3 + 2] = n("LuminanceAdjustment$c") / 100f * 0.3f
            }

            // Colour grading, with the older split-toning keys as a fallback.
            val grading = FloatArray(16)
            fun zone(i: Int, hue: Float, sat: Float, lum: Float) {
                val (r, g, b) = hueToRgb(hue / 360f)
                val k = sat / 100f * 0.25f
                grading[i * 4] = (r - 0.5f) * k
                grading[i * 4 + 1] = (g - 0.5f) * k
                grading[i * 4 + 2] = (b - 0.5f) * k
                grading[i * 4 + 3] = lum / 100f * 0.2f
            }
            val shadowHue = if (s.values.containsKey("ColorGradeShadowHue")) n("ColorGradeShadowHue") else n("SplitToningShadowHue")
            val shadowSat = if (s.values.containsKey("ColorGradeShadowSat")) n("ColorGradeShadowSat") else n("SplitToningShadowSaturation")
            val highHue = if (s.values.containsKey("ColorGradeHighlightHue")) n("ColorGradeHighlightHue") else n("SplitToningHighlightHue")
            val highSat = if (s.values.containsKey("ColorGradeHighlightSat")) n("ColorGradeHighlightSat") else n("SplitToningHighlightSaturation")
            zone(0, shadowHue, shadowSat, n("ColorGradeShadowLum"))
            zone(1, n("ColorGradeMidtoneHue"), n("ColorGradeMidtoneSat"), n("ColorGradeMidtoneLum"))
            zone(2, highHue, highSat, n("ColorGradeHighlightLum"))
            zone(3, n("ColorGradeGlobalHue"), n("ColorGradeGlobalSat"), n("ColorGradeGlobalLum"))
            val balance = if (s.values.containsKey("ColorGradeBalance")) n("ColorGradeBalance") else n("SplitToningBalance")

            val calibration = floatArrayOf(
                n("RedHue") / 100f * (20f / 360f), n("RedSaturation") / 100f,
                n("GreenHue") / 100f * (20f / 360f), n("GreenSaturation") / 100f,
                n("BlueHue") / 100f * (20f / 360f), n("BlueSaturation") / 100f,
            )

            val gray = FloatArray(8)
            PresetSettings.HSL_COLORS.forEachIndexed { i, c -> gray[i] = n("GrayMixer$c") / 100f * 0.3f }

            return RenderParams(
                wbR = wbR, wbG = wbG, wbB = wbB,
                exposure = n("Exposure2012"),
                contrast = n("Contrast2012") / 100f,
                highlights = n("Highlights2012") / 100f,
                shadows = n("Shadows2012") / 100f,
                whites = n("Whites2012") / 100f,
                blacks = n("Blacks2012") / 100f,
                vibrance = n("Vibrance") / 100f,
                saturation = n("Saturation") / 100f,
                hsl = hsl,
                grading = grading,
                gradingBalance = balance / 100f,
                gradingBlending = n("ColorGradeBlending", 50.0) / 100f,
                calibration = calibration,
                shadowTint = n("ShadowTint") / 100f,
                monochrome = s.bool("ConvertToGrayscale") || s.values["Treatment"] == "Black & White",
                grayMixer = gray,
                texture = n("Texture") / 100f,
                clarity = n("Clarity2012") / 100f,
                dehaze = n("Dehaze") / 100f,
                sharpen = n("Sharpness") / 150f,
                sharpenMasking = n("SharpenEdgeMasking") / 100f,
                noiseReduction = n("LuminanceSmoothing") / 100f,
                vignette = n("PostCropVignetteAmount") / 100f,
                vignetteMidpoint = n("PostCropVignetteMidpoint", 50.0) / 100f,
                vignetteFeather = n("PostCropVignetteFeather", 50.0) / 100f,
                vignetteRoundness = n("PostCropVignetteRoundness") / 100f,
                grain = n("GrainAmount") / 100f,
                grainSize = n("GrainSize", 25.0) / 100f,
                lut = buildLut(s),
                isIdentity = s.adjustmentKeys.none { it in RENDERED_KEYS && s.num(it) != 0.0 && it !in NON_ZERO_DEFAULTS } &&
                    PresetSettings.CURVE_KEYS.all { s.curve(it) == PresetSettings.IDENTITY_CURVE } &&
                    !s.bool("ConvertToGrayscale") && s.values["Treatment"] != "Black & White" &&
                    !(s.values["WhiteBalance"] == "Custom" && s.values.containsKey("Temperature")),
            )
        }

        /** Keys whose default is not 0 (midpoints, splits); they alone never change the picture. */
        private val NON_ZERO_DEFAULTS = setOf(
            "ColorGradeBlending", "PostCropVignetteMidpoint", "PostCropVignetteFeather", "GrainSize", "GrainFrequency",
            "ParametricShadowSplit", "ParametricMidtoneSplit", "ParametricHighlightSplit", "SharpenRadius", "SharpenDetail",
            "Temperature", "Tint",
        )

        /** Every key the renderer reads. Anything else (profile names, lens corrections…) is kept but not previewed. */
        val RENDERED_KEYS: Set<String> = buildSet {
            addAll(listOf(
                "IncrementalTemperature", "IncrementalTint", "Exposure2012", "Contrast2012", "Highlights2012", "Shadows2012",
                "Whites2012", "Blacks2012", "Vibrance", "Saturation", "Texture", "Clarity2012", "Dehaze",
                "ParametricShadows", "ParametricDarks", "ParametricLights", "ParametricHighlights",
                "ColorGradeShadowHue", "ColorGradeShadowSat", "ColorGradeShadowLum", "ColorGradeMidtoneHue",
                "ColorGradeMidtoneSat", "ColorGradeMidtoneLum", "ColorGradeHighlightHue", "ColorGradeHighlightSat",
                "ColorGradeHighlightLum", "ColorGradeGlobalHue", "ColorGradeGlobalSat", "ColorGradeGlobalLum",
                "ColorGradeBalance", "SplitToningShadowHue", "SplitToningShadowSaturation", "SplitToningHighlightHue",
                "SplitToningHighlightSaturation", "SplitToningBalance", "RedHue", "RedSaturation", "GreenHue",
                "GreenSaturation", "BlueHue", "BlueSaturation", "ShadowTint", "Sharpness", "SharpenEdgeMasking",
                "LuminanceSmoothing", "PostCropVignetteAmount", "PostCropVignetteRoundness", "GrainAmount",
            ))
            PresetSettings.HSL_COLORS.forEach { add("HueAdjustment$it"); add("SaturationAdjustment$it"); add("LuminanceAdjustment$it"); add("GrayMixer$it") }
        }

        /** Parametric curve, then the RGB point curve, then each channel's own curve. */
        fun buildLut(s: PresetSettings): FloatArray {
            val master = CurveMath.spline(s.curve("ToneCurvePV2012"))
            val red = CurveMath.spline(s.curve("ToneCurvePV2012Red"))
            val green = CurveMath.spline(s.curve("ToneCurvePV2012Green"))
            val blue = CurveMath.spline(s.curve("ToneCurvePV2012Blue"))
            val param = CurveMath.Parametric(
                shadows = s.num("ParametricShadows").toFloat() / 100f,
                darks = s.num("ParametricDarks").toFloat() / 100f,
                lights = s.num("ParametricLights").toFloat() / 100f,
                highlights = s.num("ParametricHighlights").toFloat() / 100f,
                split1 = s.num("ParametricShadowSplit", 25.0).toFloat() / 100f,
                split2 = s.num("ParametricMidtoneSplit", 50.0).toFloat() / 100f,
                split3 = s.num("ParametricHighlightSplit", 75.0).toFloat() / 100f,
            )
            val out = FloatArray(256 * 3)
            for (i in 0 until 256) {
                val x = i / 255f
                val m = master(param(x))
                out[i * 3] = red(m)
                out[i * 3 + 1] = green(m)
                out[i * 3 + 2] = blue(m)
            }
            return out
        }

        fun hueToRgb(h: Float): Triple<Float, Float, Float> {
            val x = ((h % 1f) + 1f) % 1f * 6f
            val r = (kotlin.math.abs(x - 3f) - 1f).coerceIn(0f, 1f)
            val g = (2f - kotlin.math.abs(x - 2f)).coerceIn(0f, 1f)
            val b = (2f - kotlin.math.abs(x - 4f)).coerceIn(0f, 1f)
            return Triple(r, g, b)
        }
    }
}

object CurveMath {
    /**
     * Monotone cubic (Fritsch–Carlson) through the curve's points, so a curve never overshoots
     * into banding. Returns a 0..1 → 0..1 function.
     */
    fun spline(points: List<CurvePoint>): (Float) -> Float {
        val pts = points.sortedBy { it.x }.distinctBy { it.x }
        if (pts.size < 2 || (pts.size == 2 && pts[0] == CurvePoint(0, 0) && pts[1] == CurvePoint(255, 255))) return { it }
        val xs = pts.map { it.x / 255f }
        val ys = pts.map { it.y / 255f }
        val n = xs.size
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / max(1e-6f, xs[it + 1] - xs[it]) }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
        }
        return { x ->
            when {
                x <= xs[0] -> ys[0]
                x >= xs[n - 1] -> ys[n - 1]
                else -> {
                    var k = 0
                    while (k < n - 2 && x > xs[k + 1]) k++
                    val h = xs[k + 1] - xs[k]
                    val t = (x - xs[k]) / h
                    val t2 = t * t; val t3 = t2 * t
                    ((2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * m[k] +
                        (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * m[k + 1]).coerceIn(0f, 1f)
                }
            }
        }
    }

    /** Lightroom's four-region parametric curve: each region pushes its tonal band up or down. */
    class Parametric(
        val shadows: Float, val darks: Float, val lights: Float, val highlights: Float,
        val split1: Float, val split2: Float, val split3: Float,
    ) : (Float) -> Float {
        private val identity = shadows == 0f && darks == 0f && lights == 0f && highlights == 0f

        override fun invoke(x: Float): Float {
            if (identity) return x
            fun bump(a: Float, b: Float): Float {
                if (x <= a || x >= b) return 0f
                val t = (x - a) / (b - a)
                return kotlin.math.sin(t * Math.PI.toFloat())
            }
            val y = x +
                shadows * 0.25f * bump(0f, split2) * (1f - x) +
                darks * 0.25f * bump(split1 * 0.5f, split3) +
                lights * 0.25f * bump(split1, 1f + (1f - split3) * 0.5f) +
                highlights * 0.25f * bump(split2, 1.0001f) * x
            return min(1f, max(0f, y))
        }
    }
}
