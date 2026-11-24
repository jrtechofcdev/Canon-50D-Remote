package com.example.core.render

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class EnhanceTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** A flat colour patch with luminance grain, like ISO 3200 on a wall. */
    private fun noisyPatch(w: Int, h: Int, r: Int, g: Int, b: Int, sigma: Double, seed: Int = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) {
            val n = (rnd.nextDouble() * 2 - 1) * sigma * sqrt(3.0)
            rgb((r + n).toInt(), (g + n).toInt(), (b + n).toInt())
        }
    }

    private fun channel(px: IntArray, shift: Int) = px.map { (it shr shift and 0xFF).toDouble() }
    private fun mean(v: List<Double>) = v.average()
    private fun std(v: List<Double>): Double { val m = mean(v); return sqrt(v.sumOf { (it - m) * (it - m) } / v.size) }

    @Test
    fun grainGoesDownAndTheColourStays() {
        val w = 96; val h = 96
        val src = noisyPatch(w, h, 180, 120, 90, sigma = 10.0)
        val out = Enhance.applyPixels(src, w, h, Enhance.params(Enhance.STRONG))
        for (shift in listOf(16, 8, 0)) {
            assertEquals("média do canal igual", mean(channel(src, shift)), mean(channel(out, shift)), 1.0)
        }
        val before = std(channel(src, 8))
        val after = std(channel(out, 8))
        assertTrue("granulado caiu: $before → $after", after < before * 0.6)
    }

    @Test
    fun theSameChangeGoesToEveryChannelSoHueIsKept() {
        val w = 48; val h = 48
        val src = noisyPatch(w, h, 200, 90, 40, sigma = 8.0, seed = 3)
        val out = Enhance.applyPixels(src, w, h, Enhance.params(Enhance.NORMAL))
        for (i in src.indices) {
            val dr = (out[i] shr 16 and 0xFF) - (src[i] shr 16 and 0xFF)
            val dg = (out[i] shr 8 and 0xFF) - (src[i] shr 8 and 0xFF)
            val db = (out[i] and 0xFF) - (src[i] and 0xFF)
            // Equal except where a channel hit 0 or 255.
            assertTrue("pixel $i: $dr $dg $db", abs(dr - dg) <= 1 && abs(dg - db) <= 1)
        }
    }

    @Test
    fun aHardEdgeStaysSharp() {
        val w = 64; val h = 32
        val src = IntArray(w * h) { i -> if (i % w < w / 2) rgb(40, 40, 40) else rgb(210, 210, 210) }
        val out = Enhance.applyPixels(src, w, h, Enhance.params(Enhance.STRONG))
        val row = 16 * w
        val left = out[row + w / 2 - 1] and 0xFF
        val right = out[row + w / 2] and 0xFF
        assertTrue("degrau mantido: $left → $right", right - left >= 160)
    }

    @Test
    fun aSmoothPictureIsLeftAlone() {
        val w = 64; val h = 64
        val src = IntArray(w * h) { i -> val v = 60 + (i % w) * 2; rgb(v, v, v) }
        val out = Enhance.applyPixels(src, w, h, Enhance.params(Enhance.NORMAL))
        for (i in src.indices) assertTrue("sem halo no pixel $i", abs((out[i] and 0xFF) - (src[i] and 0xFF)) <= 2)
    }

    @Test
    fun bandsGiveTheSameResultAsTheWholePicture() = runBlocking {
        val w = 80; val h = 300
        val src = noisyPatch(w, h, 120, 140, 160, sigma = 9.0, seed = 7)
        val p = Enhance.params(Enhance.NORMAL)
        val whole = Enhance.applyPixels(src, w, h, p)
        val banded = IntArray(src.size)
        Enhance.applyBands(w, h, p, workers = 3,
            read = { from, rows, dest -> System.arraycopy(src, from * w, dest, 0, rows * w) },
            write = { y0, rows, result -> System.arraycopy(result, 0, banded, y0 * w, rows * w) })
        assertArrayEquals(whole, banded)
    }

    @Test
    fun autoFollowsTheIso() {
        assertEquals(Enhance.LIGHT, Enhance.resolve(Enhance.AUTO, "ISO 200"))
        assertEquals(Enhance.NORMAL, Enhance.resolve(Enhance.AUTO, "1600"))
        assertEquals(Enhance.STRONG, Enhance.resolve(Enhance.AUTO, "3200"))
        assertEquals(Enhance.NORMAL, Enhance.resolve(Enhance.AUTO, null))
        assertEquals(Enhance.OFF, Enhance.resolve(Enhance.OFF, "3200"))
    }

    @Test
    fun aBigFrameIsQuickEnough() {
        val w = 2400; val h = 1600
        val src = noisyPatch(w, h, 128, 128, 128, sigma = 6.0)
        val started = System.nanoTime()
        Enhance.applyPixels(src, w, h, Enhance.params(Enhance.STRONG))
        val ms = (System.nanoTime() - started) / 1_000_000
        // 3.8 MP on one thread of the build machine; the phone runs bands in parallel.
        assertTrue("levou $ms ms", ms < 8_000)
    }
}
