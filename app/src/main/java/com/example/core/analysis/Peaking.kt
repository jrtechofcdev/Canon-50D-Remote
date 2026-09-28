package com.example.core.analysis

import kotlin.math.abs

/**
 * Focus peaking: marks the pixels where the picture has fine, strong contrast (what is in focus),
 * like the peaking of a cinema monitor. Works on a greyscale array; [sensitivity] 1 (only the
 * crispest edges) … 3 (more edges).
 */
object Peaking {
    fun mask(gray: IntArray, width: Int, height: Int, sensitivity: Int = 2): BooleanArray {
        val out = BooleanArray(gray.size)
        if (width < 3 || height < 3) return out
        // Laplacian response: high only where detail changes pixel to pixel, low on soft (defocused) edges.
        val response = IntArray(gray.size)
        var max = 1
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val v = abs(4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width])
                response[i] = v
                if (v > max) max = v
            }
        }
        // Absolute floor so a flat, noisy frame does not light up; relative part adapts to the scene.
        val relative = when (sensitivity) { 1 -> 0.45f; 3 -> 0.2f; else -> 0.3f }
        val threshold = maxOf(28, (max * relative).toInt())
        for (i in response.indices) out[i] = response[i] >= threshold
        return out
    }
}
