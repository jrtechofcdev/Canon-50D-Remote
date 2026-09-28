package com.example.core.render

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The same develop pipeline as [PresetShader], on the CPU. Used by tests (no GPU there) and as the
 * fallback when OpenGL is not available. Works on ARGB pixel arrays so it runs on the JVM too.
 */
object CpuRenderer {

    fun render(pixels: IntArray, width: Int, height: Int, p: RenderParams): IntArray {
        if (p.isIdentity) return pixels.copyOf()
        val small = BlurLevel(pixels, width, height, 2)
        val large = BlurLevel(pixels, width, height, 16)
        val gain = 2.0.pow(p.exposure.toDouble()).toFloat()
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val u = (x + 0.5f) / width
                val v = (y + 0.5f) / height
                out[i] = pixel(pixels[i], small.lumaAt(u, v, p, gain), large.lumaAt(u, v, p, gain), u, v, width, height, p, gain)
            }
        }
        return out
    }

    private val CENTERS = floatArrayOf(0f, 30f / 360f, 60f / 360f, 120f / 360f, 180f / 360f, 240f / 360f, 270f / 360f, 300f / 360f)

    private fun pixel(argb: Int, ls: Float, ll: Float, u: Float, v: Float, w: Int, h: Int, p: RenderParams, gain: Float): Int {
        var r = ((argb shr 16) and 0xFF) / 255f
        var g = ((argb shr 8) and 0xFF) / 255f
        var b = (argb and 0xFF) / 255f

        r = toSrgb(toLinear(r) * p.wbR * gain)
        g = toSrgb(toLinear(g) * p.wbG * gain)
        b = toSrgb(toLinear(b) * p.wbB * gain)

        val l = luma(r, g, b)
        var t = l
        t += p.highlights * 0.25f * smoothstep(0.45f, 0.95f, l)
        t += p.shadows * 0.25f * (1f - smoothstep(0.05f, 0.6f, l))
        t += p.whites * 0.15f * smoothstep(0.65f, 1f, l)
        t += p.blacks * 0.15f * (1f - smoothstep(0f, 0.35f, l))
        t = 0.5f + (t - 0.5f) * (1f + p.contrast * 0.6f)
        t += p.clarity * 0.6f * (l - ll) * 4f * l * (1f - l)
        t += p.texture * 0.8f * (l - ls)
        val haze = p.dehaze * 0.08f
        t = (t - haze) / (1f - haze)
        if (l > 1e-4f) { val k = t / l; r *= k; g *= k; b *= k } else { val d = t - l; r += d; g += d; b += d }
        r = r.coerceIn(0f, 1f); g = g.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)

        r = lut(p.lut, r, 0); g = lut(p.lut, g, 1); b = lut(p.lut, b, 2)

        val hsv = rgbToHsv(r, g, b)
        val sat0 = hsv[1]
        var hueShift = 0f; var satDelta = 0f; var lumDelta = 0f; var grayDelta = 0f
        for (i in 0 until 8) {
            val wgt = band(hsv[0], i)
            hueShift += wgt * p.hsl[i * 3]
            satDelta += wgt * p.hsl[i * 3 + 1]
            lumDelta += wgt * p.hsl[i * 3 + 2]
            grayDelta += wgt * p.grayMixer[i]
        }
        for (i in 0 until 3) {
            val wgt = band3(hsv[0], i)
            hueShift += wgt * p.calibration[i * 2]
            satDelta += wgt * p.calibration[i * 2 + 1] * 0.5f
        }
        hsv[0] = fract(hsv[0] + hueShift)
        var s = hsv[1] * (1f + satDelta)
        s *= 1f + p.vibrance * (1f - s)
        s *= 1f + p.saturation + p.dehaze * 0.2f
        hsv[1] = s.coerceIn(0f, 1f)
        val c = hsvToRgb(hsv[0], hsv[1], hsv[2])
        r = c[0] * (1f + lumDelta * sat0); g = c[1] * (1f + lumDelta * sat0); b = c[2] * (1f + lumDelta * sat0)

        val l2 = luma(r, g, b)
        val center = 0.5f - p.gradingBalance * 0.25f
        val width = mix(0.08f, 0.45f, p.gradingBlending)
        val wh = smoothstep(center - width, center + width, l2)
        val ws = 1f - wh
        val wm = max(0f, 1f - abs(l2 - center) / (width + 0.25f))
        val gr = p.grading
        r += gr[0] * ws + gr[4] * wm + gr[8] * wh + gr[12]
        g += gr[1] * ws + gr[5] * wm + gr[9] * wh + gr[13]
        b += gr[2] * ws + gr[6] * wm + gr[10] * wh + gr[14]
        val lk = 1f + gr[3] * ws + gr[7] * wm + gr[11] * wh + gr[15]
        r *= lk; g *= lk; b *= lk
        g -= p.shadowTint * 0.08f * (1f - l2)

        if (p.monochrome) { val m = luma(r, g, b) + grayDelta * sat0; r = m; g = m; b = m }

        val detail = l - ls
        val edge = if (p.sharpenMasking <= 0f) 1f else smoothstep(0f, p.sharpenMasking * 0.08f, abs(detail))
        val sharp = p.sharpen * detail * edge * 1.5f - p.noiseReduction * 0.5f * detail * (1f - edge)
        r += sharp; g += sharp; b += sharp

        var px = (u - 0.5f) * 2f
        val py = (v - 0.5f) * 2f
        px *= mix(1f, w.toFloat() / h, (p.vignetteRoundness * 0.5f + 0.5f).coerceIn(0f, 1f))
        val dist = kotlin.math.sqrt(px * px + py * py) / 1.4142f
        val vig = smoothstep(p.vignetteMidpoint - p.vignetteFeather * 0.5f, p.vignetteMidpoint + p.vignetteFeather * 0.5f + 0.001f, dist)
        if (p.vignette < 0f) { val k = 1f + p.vignette * vig; r *= k; g *= k; b *= k }
        else { val k = p.vignette * vig; r = mix(r, 1f, k); g = mix(g, 1f, k); b = mix(b, 1f, k) }

        if (p.grain != 0f) {
            val cell = 1f + p.grainSize * 3f
            val n = (hash(floor(u * w / cell), floor(v * h / cell)) - 0.5f) * p.grain * 0.12f
            r += n; g += n; b += n
        }

        return (0xFF shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
    }

    /** A box-downsampled copy used for local contrast (clarity, texture) and detail (sharpening). */
    private class BlurLevel(src: IntArray, val w: Int, val h: Int, factor: Int) {
        val bw = max(1, w / factor)
        val bh = max(1, h / factor)
        val rgb = FloatArray(bw * bh * 3)

        init {
            val counts = IntArray(bw * bh)
            for (y in 0 until h) for (x in 0 until w) {
                val bx = min(bw - 1, x * bw / w)
                val by = min(bh - 1, y * bh / h)
                val k = by * bw + bx
                val c = src[y * w + x]
                rgb[k * 3] += ((c shr 16) and 0xFF) / 255f
                rgb[k * 3 + 1] += ((c shr 8) and 0xFF) / 255f
                rgb[k * 3 + 2] += (c and 0xFF) / 255f
                counts[k]++
            }
            for (k in counts.indices) if (counts[k] > 0) for (j in 0..2) rgb[k * 3 + j] /= counts[k]
        }

        fun lumaAt(u: Float, v: Float, p: RenderParams, gain: Float): Float {
            val fx = (u * bw - 0.5f).coerceIn(0f, (bw - 1).toFloat())
            val fy = (v * bh - 0.5f).coerceIn(0f, (bh - 1).toFloat())
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val x1 = min(bw - 1, x0 + 1); val y1 = min(bh - 1, y0 + 1)
            val ax = fx - x0; val ay = fy - y0
            val out = FloatArray(3)
            for (j in 0..2) {
                val top = mix(rgb[(y0 * bw + x0) * 3 + j], rgb[(y0 * bw + x1) * 3 + j], ax)
                val bottom = mix(rgb[(y1 * bw + x0) * 3 + j], rgb[(y1 * bw + x1) * 3 + j], ax)
                out[j] = mix(top, bottom, ay)
            }
            return luma(
                toSrgb(toLinear(out[0]) * p.wbR * gain),
                toSrgb(toLinear(out[1]) * p.wbG * gain),
                toSrgb(toLinear(out[2]) * p.wbB * gain),
            )
        }
    }

    private fun lut(t: FloatArray, x: Float, ch: Int): Float {
        val f = x.coerceIn(0f, 1f) * 255f
        val i0 = f.toInt().coerceIn(0, 255)
        val i1 = min(255, i0 + 1)
        return mix(t[i0 * 3 + ch], t[i1 * 3 + ch], f - i0)
    }

    private fun band(h: Float, i: Int): Float {
        val c = CENTERS[i]
        val wl = hueDist(c, CENTERS[(i + 7) % 8])
        val wr = hueDist(c, CENTERS[(i + 1) % 8])
        var d = h - c
        d -= floor(d + 0.5f)
        return if (d < 0f) max(0f, 1f + d / wl) else max(0f, 1f - d / wr)
    }

    private fun band3(h: Float, i: Int): Float {
        var d = h - i / 3f
        d -= floor(d + 0.5f)
        return max(0f, 1f - abs(d) * 3f)
    }

    private fun hueDist(a: Float, b: Float): Float { val d = abs(a - b); return min(d, 1f - d) }

    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        var h = 0f
        if (d > 1e-6f) {
            h = when (mx) {
                r -> ((g - b) / d).let { it - 6f * floor(it / 6f) }
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } / 6f
        }
        return floatArrayOf(h, if (mx > 1e-6f) d / mx else 0f, mx)
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        fun ch(offset: Float): Float {
            val k = abs(fract(h + offset) * 6f - 3f) - 1f
            return v * mix(1f, k.coerceIn(0f, 1f), s)
        }
        return floatArrayOf(ch(1f), ch(2f / 3f), ch(1f / 3f))
    }

    private fun toLinear(c: Float) = if (c < 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    private fun toSrgb(c0: Float): Float { val c = max(0f, c0); return if (c < 0.0031308f) c * 12.92f else (1.055 * c.toDouble().pow(1 / 2.4) - 0.055).toFloat() }
    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
    private fun smoothstep(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun fract(x: Float) = x - floor(x)
    private fun hash(x: Float, y: Float): Float = fract(sin(x * 12.9898f + y * 78.233f) * 43758.547f)
    private fun to8(c: Float) = (c.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
