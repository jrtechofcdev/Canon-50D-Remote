package com.example.core.render

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Melhorar": less high-ISO grain and a little more definition, on the phone, without touching colour.
 *
 * Works on luminance only. An edge-preserving guided filter (the image guides itself) smooths the grain
 * while keeping edges, then a small unsharp mask brings the definition back where there are real edges.
 * The change in luminance is added equally to R, G and B, so hue and colour stay as they were.
 * Pure Kotlin on ARGB pixels, processed in horizontal bands so a 15 MP frame never needs more than a
 * few MB of work memory per band.
 */
object Enhance {

    /** Strength codes stored on the photo ([com.example.core.data.Photo.enhance]). */
    const val OFF = 0
    const val LIGHT = 1
    const val NORMAL = 2
    const val STRONG = 3
    /** Picked from the photo's ISO when it is rendered. */
    const val AUTO = 9

    val LEVELS = listOf(LIGHT, NORMAL, STRONG)

    fun label(code: Int) = when (code) {
        LIGHT -> "Leve"
        NORMAL -> "Normal"
        STRONG -> "Forte"
        AUTO -> "Automático (pelo ISO)"
        else -> "Desligado"
    }

    /** The strength actually used: [code] itself, or for [AUTO] one picked from the ISO ("ISO 3200", "3200"). */
    fun resolve(code: Int, iso: String?): Int = when (code) {
        LIGHT, NORMAL, STRONG -> code
        AUTO -> {
            val value = iso?.filter { it.isDigit() }?.toIntOrNull() ?: 800
            when {
                value <= 400 -> LIGHT
                value < 3200 -> NORMAL
                else -> STRONG
            }
        }
        else -> OFF
    }

    /**
     * Filter settings for a strength. [scale] is the picture's size against a full 50D frame: a preview
     * already averaged the grain away, so its radius shrinks with it.
     */
    data class Params(val radius: Int, val eps: Float, val sharpen: Float) {
        /** Rows of context a band needs on each side: two box filters of [radius] plus the sharpen blur. */
        val apron get() = 2 * radius + 2
    }

    fun params(strength: Int, scale: Float = 1f): Params {
        val s = scale.coerceIn(0.25f, 1f)
        fun r(full: Int) = max(1, (full * s).roundToInt())
        return when (strength) {
            LIGHT -> Params(r(2), 0.0006f, 0.30f)
            STRONG -> Params(r(3), 0.0040f, 0.55f)
            else -> Params(r(2), 0.0018f, 0.45f)
        }
    }

    /** Scale for a picture whose long edge is [longEdge] (a 50D frame is 4752 px). */
    fun scaleFor(longEdge: Int) = (longEdge / FULL_EDGE).coerceIn(0.25f, 1f)

    /** The whole picture at once (tests, small pictures). Returns a new array; [pixels] is not changed. */
    fun applyPixels(pixels: IntArray, w: Int, h: Int, p: Params): IntArray {
        val out = IntArray(pixels.size)
        var y = 0
        while (y < h) {
            val rows = min(BAND, h - y)
            band(w, h, y, rows, p, read = { from, count, dest -> System.arraycopy(pixels, from * w, dest, 0, count * w) }) { result ->
                System.arraycopy(result, 0, out, y * w, rows * w)
            }
            y += rows
        }
        return out
    }

    /**
     * Band by band with [workers] in parallel: [read] fills a buffer with rows of the original picture,
     * [write] receives each finished band (rows [y0] to [y0] + rows). The original is only read, so the
     * output may be a different bitmap or array.
     */
    suspend fun applyBands(
        w: Int,
        h: Int,
        p: Params,
        workers: Int = min(3, Runtime.getRuntime().availableProcessors()),
        read: (fromRow: Int, rows: Int, dest: IntArray) -> Unit,
        write: (y0: Int, rows: Int, result: IntArray) -> Unit,
    ) = coroutineScope {
        val gate = Semaphore(max(1, workers))
        (0 until h step BAND).map { y ->
            async(Dispatchers.Default) {
                gate.withPermit {
                    val rows = min(BAND, h - y)
                    band(w, h, y, rows, p, read = { from, count, dest -> synchronized(gate) { read(from, count, dest) } }) { result ->
                        synchronized(gate) { write(y, rows, result) }
                    }
                }
            }
        }.awaitAll()
    }

    /** One band: rows [y0] to [y0] + [rows] of an h-row picture, with the apron read around it. */
    private inline fun band(
        w: Int,
        h: Int,
        y0: Int,
        rows: Int,
        p: Params,
        read: (fromRow: Int, rows: Int, dest: IntArray) -> Unit,
        write: (IntArray) -> Unit,
    ) {
        val top = max(0, y0 - p.apron)
        val bottom = min(h, y0 + rows + p.apron)
        val inRows = bottom - top
        val src = IntArray(w * inRows)
        read(top, inRows, src)
        val n = w * inRows
        val y = FloatArray(n)
        for (i in 0 until n) y[i] = luma(src[i])

        // Guided filter, the picture as its own guide: mean and variance in a (2r+1)² window.
        val meanI = FloatArray(n)
        val meanII = FloatArray(n)
        val tmp = FloatArray(n)
        box(y, meanI, tmp, w, inRows, p.radius)
        for (i in 0 until n) meanII[i] = y[i] * y[i]
        box(meanII, meanII, tmp, w, inRows, p.radius)
        // a and b of each window, kept in meanII and meanI.
        for (i in 0 until n) {
            val m = meanI[i]
            val variance = max(0f, meanII[i] - m * m)
            val a = variance / (variance + p.eps)
            meanII[i] = a
            meanI[i] = m - a * m
        }
        box(meanII, meanII, tmp, w, inRows, p.radius)
        box(meanI, meanI, tmp, w, inRows, p.radius)
        val q = meanII
        for (i in 0 until n) q[i] = meanII[i] * y[i] + meanI[i]

        // Definition back on real edges only: a 3×3 unsharp mask gated by how strong the detail is.
        val blur = meanI
        box(q, blur, tmp, w, inRows, 1)
        val out = IntArray(w * rows)
        val offset = (y0 - top) * w
        for (i in 0 until w * rows) {
            val k = offset + i
            val detail = q[k] - blur[k]
            val mask = smoothstep(EDGE_LOW, EDGE_HIGH, abs(detail))
            val target = q[k] + p.sharpen * detail * mask
            val delta = ((target - y[k]) * 255f).roundToInt()
            val c = src[k]
            out[i] = (c and 0xFF000000.toInt()) or
                (clamp((c shr 16 and 0xFF) + delta) shl 16) or
                (clamp((c shr 8 and 0xFF) + delta) shl 8) or
                clamp((c and 0xFF) + delta)
        }
        write(out)
    }

    /** Separable box mean of radius [r] (edges repeated), from [src] into [dst]; [src] and [dst] may be the same. */
    private fun box(src: FloatArray, dst: FloatArray, tmp: FloatArray, w: Int, h: Int, r: Int) {
        val size = 2f * r + 1f
        for (row in 0 until h) {
            val base = row * w
            var sum = 0f
            for (k in -r..r) sum += src[base + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[base + x] = sum / size
                sum += src[base + min(w - 1, x + r + 1)] - src[base + max(0, x - r)]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (row in 0 until h) {
                dst[row * w + x] = sum / size
                sum += tmp[min(h - 1, row + r + 1) * w + x] - tmp[max(0, row - r) * w + x]
            }
        }
    }

    private fun luma(c: Int): Float =
        (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private const val BAND = 128
    private const val FULL_EDGE = 4752f
    // Luminance detail below this is grain (not sharpened); above the second value it is a real edge.
    private const val EDGE_LOW = 0.006f
    private const val EDGE_HIGH = 0.03f
}
