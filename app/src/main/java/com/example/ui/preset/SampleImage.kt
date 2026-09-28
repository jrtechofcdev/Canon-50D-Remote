package com.example.ui.preset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Stand-in photo for editing a preset before any frame has arrived: sky, warm sun, skin-like tone,
 * foliage and deep shadow, so every panel (WB, HSL, curve, grading) has something to act on.
 */
object SampleImage {
    private const val W = 1200
    private const val H = 800

    fun uri(context: Context): String {
        val file = File(context.cacheDir, "preset_sample_v1.jpg")
        if (!file.exists()) {
            val bitmap = draw()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        return "file://${file.absolutePath}"
    }

    private fun draw(): Bitmap {
        val pixels = IntArray(W * H)
        for (y in 0 until H) {
            val v = y / H.toFloat()
            for (x in 0 until W) {
                val u = x / W.toFloat()
                // Sky: blue at the top fading to a warm horizon.
                var r = 70 + 150 * v
                var g = 130 + 80 * v
                var b = 220 - 90 * v
                // Sun glow.
                val sun = exp(-(hypot(u - 0.78f, v - 0.35f) * 7f).let { it * it }).toFloat()
                r += 185 * sun; g += 140 * sun; b += 40 * sun
                // Foliage band with some texture.
                val hill = 0.62f + 0.05f * sin(u * 9f)
                if (v > hill) {
                    val t = (v - hill) / (1 - hill)
                    val leaf = 0.5f + 0.5f * sin(x * 0.21f) * sin(y * 0.17f)
                    r = 40 + 50 * leaf - 30 * t; g = 95 + 60 * leaf - 70 * t; b = 35 + 20 * leaf - 25 * t
                }
                // Skin-toned figure in the left third.
                val body = hypot((u - 0.3f) / 0.09f, (v - 0.55f) / 0.28f)
                if (body < 1f) {
                    val shade = 0.75f + 0.25f * (1 - body)
                    r = 214 * shade; g = 160 * shade; b = 128 * shade
                }
                pixels[y * W + x] = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
            }
        }
        return Bitmap.createBitmap(pixels, W, H, Bitmap.Config.ARGB_8888)
    }
}
