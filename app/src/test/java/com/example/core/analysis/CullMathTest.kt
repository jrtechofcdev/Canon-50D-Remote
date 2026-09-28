package com.example.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CullMathTest {
    private val w = 320
    private val h = 240

    /** Checkerboard-ish detail with noise: what a sharp textured subject looks like. */
    private fun sharpImage(seed: Long = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val base = if ((x / 4 + y / 4) % 2 == 0) 60 else 190
            (base + rnd.nextInt(20)).coerceIn(0, 255)
        }
    }

    /** The same picture after a 9 × 9 box blur: motion blur or missed focus. */
    private fun blur(src: IntArray, r: Int = 4): IntArray = IntArray(src.size) { i ->
        val x = i % w; val y = i / w
        var s = 0; var n = 0
        for (dy in -r..r) for (dx in -r..r) {
            val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1)
            s += src[yy * w + xx]; n++
        }
        s / n
    }

    @Test
    fun blurredFrameScoresLower() {
        val sharp = sharpImage()
        val soft = blur(sharp)
        val a = CullMath.sharpness(sharp, w, h)
        val b = CullMath.sharpness(soft, w, h)
        assertTrue("sharp $a vs blurred $b", a > 60 && b < a - 25)
    }

    @Test
    fun faceRegionDecides() {
        // Only the face area is soft: measured on the face, the frame reads as blurred.
        val img = sharpImage()
        val soft = blur(img)
        val face = Region(100, 60, 200, 160)
        for (y in face.top until face.bottom) for (x in face.left until face.right) img[y * w + x] = soft[y * w + x]
        assertTrue(CullMath.sharpness(img, w, h, listOf(face)) < CullMath.sharpness(img, w, h) - 20)
    }

    @Test
    fun hashFindsNearDuplicates() {
        val a = sharpImage(1)
        val gradient = IntArray(w * h) { i -> (i % w) * 255 / w }
        val slightlyDifferent = IntArray(w * h) { i -> (gradient[i] + (i % 7)).coerceIn(0, 255) }
        assertTrue(CullMath.similar(CullMath.dHash(gradient, w, h), CullMath.dHash(slightlyDifferent, w, h)))
        val reversed = IntArray(w * h) { i -> 255 - gradient[i] }
        assertTrue(!CullMath.similar(CullMath.dHash(gradient, w, h), CullMath.dHash(reversed, w, h)))
        assertEquals(0, CullMath.distance(CullMath.dHash(a, w, h), CullMath.dHash(a, w, h)))
    }

    @Test
    fun scoreRewardsOpenEyesAndSharpness() {
        val good = CullMath.score(85f, faces = 3, eyesClosed = 0, clippedPercent = 0.2f, darkPercent = 2f, faceSharpness = 80f)
        val blink = CullMath.score(85f, faces = 3, eyesClosed = 2, clippedPercent = 0.2f, darkPercent = 2f, faceSharpness = 80f)
        val shaken = CullMath.score(25f, faces = 3, eyesClosed = 0, clippedPercent = 0.2f, darkPercent = 2f, faceSharpness = 20f)
        assertTrue(good > 85)
        assertTrue(blink < good - 15)
        assertTrue(shaken < good - 25)
    }
}
