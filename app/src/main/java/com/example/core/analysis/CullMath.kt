package com.example.core.analysis

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A region of the image, in pixels of the analysed (reduced) picture. */
data class Region(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Pure maths behind the culling: how sharp a frame is, how alike two frames are, and a 0-100 score.
 * Works on greyscale arrays so it runs the same on the phone and in unit tests.
 */
object CullMath {

    fun luma(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val p = pixels[i]
        (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
    }

    /**
     * Sharpness 0-100 from the variance of the Laplacian. The frame is split in tiles and the best tiles
     * count, because a portrait with a soft background is still sharp where the eyes are. With [regions]
     * (faces), only those areas are measured.
     */
    fun sharpness(gray: IntArray, width: Int, height: Int, regions: List<Region> = emptyList()): Float {
        val areas = regions.map { it.clamp(width, height) }.filter { it.right - it.left >= 8 && it.bottom - it.top >= 8 }
        val variance = if (areas.isNotEmpty()) {
            areas.maxOf { laplacianVariance(gray, width, it) }
        } else {
            val tile = max(16, min(width, height) / 6)
            val scores = mutableListOf<Double>()
            var y = 0
            while (y + tile <= height) {
                var x = 0
                while (x + tile <= width) {
                    scores += laplacianVariance(gray, width, Region(x, y, x + tile, y + tile))
                    x += tile
                }
                y += tile
            }
            if (scores.isEmpty()) laplacianVariance(gray, width, Region(0, 0, width, height))
            else scores.sortedDescending().take(max(1, scores.size / 8)).average()
        }
        // Log scale at 1024 px: variance under ~50 in the best area is a shaken or missed frame (score < 50),
        // a few hundred is sharp, 3000+ is crisp fine detail (100).
        return (ln(1 + variance) / ln(1 + 3000.0) * 100).coerceIn(0.0, 100.0).toFloat()
    }

    private fun Region.clamp(w: Int, h: Int) = Region(left.coerceIn(0, w), top.coerceIn(0, h), right.coerceIn(0, w), bottom.coerceIn(0, h))

    private fun laplacianVariance(g: IntArray, width: Int, r: Region): Double {
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in max(1, r.top) until min(r.bottom, g.size / width - 1)) {
            val row = y * width
            for (x in max(1, r.left) until min(r.right, width - 1)) {
                val i = row + x
                val lap = (g[i - 1] + g[i + 1] + g[i - width] + g[i + width] - 4 * g[i]).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** 64-bit difference hash: near-identical frames (a burst, the same pose) differ in only a few bits. */
    fun dHash(gray: IntArray, width: Int, height: Int): Long {
        var hash = 0L
        var bit = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val a = sample(gray, width, height, col, row)
                val b = sample(gray, width, height, col + 1, row)
                if (a > b) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Average of the cell (cx, cy) in a 9 × 8 grid over the picture. */
    private fun sample(g: IntArray, w: Int, h: Int, cx: Int, cy: Int): Int {
        val x0 = cx * w / 9
        val x1 = max(x0 + 1, (cx + 1) * w / 9)
        val y0 = cy * h / 8
        val y1 = max(y0 + 1, (cy + 1) * h / 8)
        var sum = 0L
        var n = 0
        val step = max(1, (x1 - x0) / 8)
        for (y in y0 until y1 step step) for (x in x0 until x1 step step) { sum += g[y * w + x]; n++ }
        return if (n == 0) 0 else (sum / n).toInt()
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun similar(a: Long?, b: Long?, maxBits: Int = 10): Boolean = a != null && b != null && distance(a, b) <= maxBits

    /**
     * 0-100: sharpness 40, open eyes 30, exposure 20, sharp faces 10.
     * [clippedPercent] is the blown-highlight share; [darkPercent] the share of near-black pixels.
     */
    fun score(sharpness: Float, faces: Int?, eyesClosed: Int, clippedPercent: Float, darkPercent: Float, faceSharpness: Float?): Float {
        val eyes = when {
            faces == null || faces == 0 -> 1f
            else -> 1f - eyesClosed.toFloat() / faces
        }
        val exposure = (1f - (clippedPercent / 5f).coerceIn(0f, 1f) * 0.6f - (darkPercent / 40f).coerceIn(0f, 1f) * 0.4f).coerceIn(0f, 1f)
        val faceFocus = (faceSharpness ?: sharpness) / 100f
        return (sharpness * 0.4f + eyes * 30f + exposure * 20f + faceFocus * 10f).coerceIn(0f, 100f)
    }

    fun percentDark(gray: IntArray, level: Int = 12): Float =
        if (gray.isEmpty()) 0f else (gray.count { it <= level } * 100f / gray.size)

    fun rounded(v: Float) = (v * 10).roundToInt() / 10f
}
