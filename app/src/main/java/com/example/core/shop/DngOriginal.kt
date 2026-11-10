package com.example.core.shop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs
import kotlin.math.pow

/**
 * Turns the original photo stored in a DNG preset (LinearRaw) into a normal picture, for the
 * "segure para ver sem o preset" comparison, like Lightroom's before/after.
 *
 * The preset DNGs of the API come from JPEGs: their data is linear sRGB, so the before is the
 * linear values put back on the sRGB curve. When the file says otherwise (a camera colour matrix
 * and white balance), those are applied too, as a plain DNG reader would.
 */
object DngOriginal {

    /** [bytes] holds the file from [DngLayout.RawImage.start] to [DngLayout.RawImage.end]; the result fits [maxEdge]. */
    fun render(image: DngLayout.RawImage, layout: DngLayout, bytes: ByteArray, maxEdge: Int): Bitmap? {
        var step = 1
        while (image.maxEdge / (step * 2) >= maxEdge) step *= 2
        val outW = (image.width + step - 1) / step
        val outH = (image.height + step - 1) / step
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val tone = Tone.of(image, layout)
        val across = (image.width + image.tileWidth - 1) / image.tileWidth
        var drawn = 0
        for (t in image.offsets.indices) {
            val from = (image.offsets[t] - image.start).toInt()
            val len = image.counts[t].toInt()
            if (from < 0 || from + len > bytes.size) continue
            val tile = bytes.copyOfRange(from, from + len)
            val tx = (t % across) * image.tileWidth
            val ty = (t / across) * image.tileHeight
            val ok = when (image.compression) {
                7 -> lossless(tile, image, tone, out, tx, ty, step)
                else -> lossy(tile, image, tone, out, tx, ty, step)
            }
            if (ok) drawn++
        }
        return out.takeIf { drawn > 0 }
    }

    /** A JPEG tile (lossy DNG): BitmapFactory reads it, already shrunk, then each value goes through the tone. */
    private fun lossy(tile: ByteArray, image: DngLayout.RawImage, tone: Tone, out: Bitmap, tx: Int, ty: Int, step: Int): Boolean {
        val bmp = BitmapFactory.decodeByteArray(tile, 0, tile.size, BitmapFactory.Options().apply { inSampleSize = step }) ?: return false
        val x0 = tx / step
        val y0 = ty / step
        val w = minOf(bmp.width, out.width - x0, (image.tileWidth + step - 1) / step)
        val h = minOf(bmp.height, out.height - y0, (image.tileHeight + step - 1) / step)
        if (w <= 0 || h <= 0) return false
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        for (i in px.indices) {
            val c = px[i]
            px[i] = tone.pixel((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }
        out.setPixels(px, 0, w, x0, y0, w, h)
        return true
    }

    /** A lossless JPEG tile: decoded here, then every [step]th pixel is kept. */
    private fun lossless(tile: ByteArray, image: DngLayout.RawImage, tone: Tone, out: Bitmap, tx: Int, ty: Int, step: Int): Boolean {
        val img = runCatching { LosslessJpeg.decode(tile) }.getOrNull() ?: return false
        // DNG may encode a tile as N interleaved components per JPEG row; the flat order is the same.
        val flat = img.samples
        val rowSamples = image.tileWidth * 3
        val x0 = tx / step
        val y0 = ty / step
        val w = minOf((image.tileWidth + step - 1) / step, out.width - x0, (image.width - tx + step - 1) / step)
        val h = minOf((image.tileHeight + step - 1) / step, out.height - y0, (image.height - ty + step - 1) / step)
        if (w <= 0 || h <= 0) return false
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * step * rowSamples
            for (x in 0 until w) {
                val i = row + x * step * 3
                if (i + 2 >= flat.size) continue
                px[y * w + x] = tone.pixel(flat[i], flat[i + 1], flat[i + 2])
            }
        }
        out.setPixels(px, 0, w, x0, y0, w, h)
        return true
    }

    /** Stored value → display pixel: linearization, black/white levels, colour, sRGB curve. */
    private class Tone(private val linear: FloatArray, private val matrix: FloatArray?) {
        fun pixel(r: Int, g: Int, b: Int): Int {
            var lr = linear[r.coerceIn(0, linear.size - 1)]
            var lg = linear[g.coerceIn(0, linear.size - 1)]
            var lb = linear[b.coerceIn(0, linear.size - 1)]
            val m = matrix
            if (m != null) {
                val nr = m[0] * lr + m[1] * lg + m[2] * lb
                val ng = m[3] * lr + m[4] * lg + m[5] * lb
                val nb = m[6] * lr + m[7] * lg + m[8] * lb
                lr = nr; lg = ng; lb = nb
            }
            return (0xFF shl 24) or (SRGB[index(lr)] shl 16) or (SRGB[index(lg)] shl 8) or SRGB[index(lb)]
        }

        companion object {
            private const val STEPS = 4096
            private val SRGB = IntArray(STEPS + 1) { i ->
                val v = i.toDouble() / STEPS
                val s = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
                (s * 255 + 0.5).toInt().coerceIn(0, 255)
            }

            private fun index(v: Float): Int = (v * STEPS + 0.5f).toInt().coerceIn(0, STEPS)

            fun of(image: DngLayout.RawImage, layout: DngLayout): Tone {
                val size = if (image.compression == 7) 1 shl image.bits.coerceIn(1, 16) else 256
                val range = (image.white - image.black).takeIf { it > 0 } ?: 1.0
                val table = image.linearization
                val linear = FloatArray(size) { v ->
                    val raw = table?.let { it[v.coerceAtMost(it.size - 1)].toDouble() } ?: v.toDouble()
                    ((raw - image.black) / range).coerceIn(0.0, 1.0).toFloat()
                }
                return Tone(linear, cameraToSrgb(layout.colorMatrix, layout.neutral))
            }

            // XYZ (D50) → linear sRGB, Bradford-adapted: the matrix Adobe writes for sRGB sources.
            private val XYZ_TO_SRGB = doubleArrayOf(
                3.1338561, -1.6168667, -0.4906146,
                -0.9787684, 1.9161415, 0.0334540,
                0.0719453, -0.2289914, 1.4052427,
            )

            /**
             * Camera → sRGB, white-balanced so the camera's neutral comes out grey. Null when the file is
             * already sRGB (the usual case for these presets), so the fast path skips the matrix.
             */
            fun cameraToSrgb(cm: DoubleArray?, neutral: DoubleArray?): FloatArray? {
                if (cm == null || cm.size != 9) return null
                val n = neutral?.takeIf { it.size == 3 && it.all { v -> v > 0 } } ?: doubleArrayOf(1.0, 1.0, 1.0)
                val isSrgb = cm.indices.all { abs(cm[it] - XYZ_TO_SRGB[it]) < 0.02 } && n.all { abs(it - 1.0) < 0.02 }
                if (isSrgb) return null
                val camToXyz = invert(cm) ?: return null
                val t = multiply(XYZ_TO_SRGB, camToXyz)
                // Balance: divide by the neutral, then scale each row so a neutral grey stays grey.
                val m = DoubleArray(9) { t[it] / n[it % 3] }
                for (row in 0..2) {
                    val sum = (0..2).sumOf { m[row * 3 + it] * n[it] }
                    if (sum != 0.0) for (c in 0..2) m[row * 3 + c] /= sum
                }
                return FloatArray(9) { m[it].toFloat() }
            }

            private fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i ->
                val r = i / 3
                val c = i % 3
                (0..2).sumOf { a[r * 3 + it] * b[it * 3 + c] }
            }

            private fun invert(m: DoubleArray): DoubleArray? {
                val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
                if (abs(det) < 1e-9) return null
                return doubleArrayOf(
                    (m[4] * m[8] - m[5] * m[7]) / det, (m[2] * m[7] - m[1] * m[8]) / det, (m[1] * m[5] - m[2] * m[4]) / det,
                    (m[5] * m[6] - m[3] * m[8]) / det, (m[0] * m[8] - m[2] * m[6]) / det, (m[2] * m[3] - m[0] * m[5]) / det,
                    (m[3] * m[7] - m[4] * m[6]) / det, (m[1] * m[6] - m[0] * m[7]) / det, (m[0] * m[4] - m[1] * m[3]) / det,
                )
            }
        }
    }
}
