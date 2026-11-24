package com.example.core.render

import java.util.Locale

/**
 * [RenderParams] for a web page that runs [PresetShader] in WebGL2. Keys are the shader's uniform
 * names, so the page sets each one as it comes; the LUT goes as the same 0..255 bytes the app
 * uploads (R, G, B per step), so the curves match to the byte.
 */
object RenderParamsJson {

    fun encode(p: RenderParams): String = buildString {
        append('{')
        vec("uWb", floatArrayOf(p.wbR, p.wbG, p.wbB))
        num("uExposure", p.exposure)
        num("uContrast", p.contrast)
        num("uHighlights", p.highlights)
        num("uShadows", p.shadows)
        num("uWhites", p.whites)
        num("uBlacks", p.blacks)
        num("uVibrance", p.vibrance)
        num("uSaturation", p.saturation)
        vec("uHsl", p.hsl)
        vec("uGrading", p.grading)
        num("uBalance", p.gradingBalance)
        num("uBlending", p.gradingBlending)
        vec("uCalib", p.calibration)
        num("uShadowTint", p.shadowTint)
        append("\"uMono\":").append(if (p.monochrome) 1 else 0).append(',')
        vec("uGray", p.grayMixer)
        num("uTexture", p.texture)
        num("uClarity", p.clarity)
        num("uDehaze", p.dehaze)
        num("uSharpen", p.sharpen)
        num("uMasking", p.sharpenMasking)
        num("uNoise", p.noiseReduction)
        num("uVignette", p.vignette)
        num("uVigMid", p.vignetteMidpoint)
        num("uVigFeather", p.vignetteFeather)
        num("uVigRound", p.vignetteRoundness)
        num("uGrain", p.grain)
        num("uGrainSize", p.grainSize)
        append("\"lut\":[")
        for (i in 0 until 256 * 3) {
            if (i > 0) append(',')
            append(lutByte(p.lut[i]))
        }
        append("]}")
    }

    /** The byte the app's GL renderer uploads for one LUT value. */
    fun lutByte(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    private fun StringBuilder.num(key: String, v: Float) {
        append('"').append(key).append("\":").append(f(v)).append(',')
    }

    private fun StringBuilder.vec(key: String, v: FloatArray) {
        append('"').append(key).append("\":[")
        v.forEachIndexed { i, x -> if (i > 0) append(','); append(f(x)) }
        append("],")
    }

    /** Plain decimal, never NaN or Infinity (JSON has neither). */
    private fun f(v: Float): String = if (v.isFinite()) String.format(Locale.ROOT, "%.6f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" } else "0"
}
