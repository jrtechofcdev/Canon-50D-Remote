package com.example.core.shop

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class LosslessJpegTest {

    /**
     * A small lossless JPEG encoder (SOF3, one Huffman table with every size category), enough to
     * check that decoding gives back exactly the samples that went in.
     */
    private fun encode(w: Int, h: Int, comps: Int, precision: Int, predictor: Int, samples: IntArray): ByteArray {
        // Size categories 0..16, codes of 5 bits each: simple and valid.
        val counts = IntArray(17).also { it[5] = 17 }
        val values = IntArray(17) { it }
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8); out.write(v and 0xFF) }
        u16(0xFFD8)
        u16(0xFFC4); u16(2 + 1 + 16 + 17); out.write(0x00)
        for (i in 1..16) out.write(counts[i])
        values.forEach { out.write(it) }
        u16(0xFFC3); u16(8 + comps * 3); out.write(precision); u16(h); u16(w); out.write(comps)
        for (c in 0 until comps) { out.write(c + 1); out.write(0x11); out.write(0) }
        u16(0xFFDA); u16(6 + comps * 2); out.write(comps)
        for (c in 0 until comps) { out.write(c + 1); out.write(0x00) }
        out.write(predictor); out.write(0); out.write(0)

        var acc = 0L
        var n = 0
        fun put(value: Int, bits: Int) {
            acc = (acc shl bits) or (value.toLong() and ((1L shl bits) - 1)); n += bits
            while (n >= 8) {
                val b = ((acc shr (n - 8)) and 0xFF).toInt()
                out.write(b); if (b == 0xFF) out.write(0)
                n -= 8
            }
        }
        val row = w * comps
        val mask = (1 shl precision) - 1
        for (y in 0 until h) for (x in 0 until w) for (c in 0 until comps) {
            val i = y * row + x * comps + c
            val pred = when {
                y == 0 && x == 0 -> 1 shl (precision - 1)
                y == 0 -> samples[i - comps]
                x == 0 -> samples[i - row]
                else -> {
                    val ra = samples[i - comps]; val rb = samples[i - row]; val rc = samples[i - row - comps]
                    when (predictor) { 1 -> ra; 2 -> rb; 3 -> rc; 4 -> ra + rb - rc; 5 -> ra + ((rb - rc) shr 1); 6 -> rb + ((ra - rc) shr 1); else -> (ra + rb) shr 1 }
                }
            }
            var diff = (samples[i] - pred) and mask
            if (diff >= 1 shl (precision - 1)) diff -= 1 shl precision
            val size = if (diff == 0) 0 else 32 - Integer.numberOfLeadingZeros(kotlin.math.abs(diff))
            put(size, 5)
            if (size in 1..15) put(if (diff < 0) diff - 1 else diff, size)
        }
        if (n > 0) put(0x7F, 8 - n)
        u16(0xFFD9)
        return out.toByteArray()
    }

    private fun image(w: Int, h: Int, comps: Int, max: Int) = IntArray(w * h * comps) { i ->
        val p = i / comps
        val x = p % w
        val y = p / w
        ((x * 7 + y * 13 + (i % comps) * 40 + (x * y) % 17) * max / (w * 7 + h * 13 + 120)).coerceIn(0, max)
    }

    @Test
    fun decodesEightBitRgbExactly() {
        val px = image(40, 24, 3, 255)
        val img = LosslessJpeg.decode(encode(40, 24, 3, 8, 1, px))
        assertEquals(40, img.width)
        assertEquals(24, img.height)
        assertEquals(3, img.components)
        assertArrayEquals(px, img.samples)
    }

    @Test
    fun decodesEveryPredictorAndSixteenBits() {
        for (predictor in 1..7) {
            val px = image(19, 11, 3, 65535)
            val img = LosslessJpeg.decode(encode(19, 11, 3, 16, predictor, px))
            assertArrayEquals("predictor $predictor", px, img.samples)
        }
    }
}
