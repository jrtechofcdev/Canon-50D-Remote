package com.example.core.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.core.analysis.CullMath
import com.example.core.analysis.ImageAnalyzer
import com.example.core.analysis.Peaking
import com.example.core.render.PresetEngine
import com.example.core.render.RenderParams

/** What the monitor draws for one frame: the picture (with the look) and the optional overlays. */
class LiveFrame(
    val picture: Bitmap,
    val zebra: Bitmap?,
    val peaking: Bitmap?,
    /** 64-bin histograms (luma, red, green, blue), or null when off. */
    val histogram: List<IntArray>?,
)

data class LiveOptions(
    val look: RenderParams? = null,
    val zebra: Boolean = false,
    val zebraLevel: Int = 250,
    val peaking: Boolean = false,
    val peakingColor: Int = 0xFF00FCA8.toInt(),
    val peakingSensitivity: Int = 2,
    val histogram: Boolean = true,
)

/**
 * Turns live view JPEGs into monitor frames. The look runs on the GPU (like a colour profile on a
 * cinema monitor); zebra, peaking and histogram are measured on the picture as shown, look included,
 * at half size to stay light on a 4 GB phone.
 */
class LiveProcessor(private val engine: PresetEngine) {

    // Three decode buffers taken in turn: no new 3 MB bitmap per frame (the main source of GC stutter),
    // and the screen is never drawing the buffer being written.
    private val pool = arrayOfNulls<Bitmap>(POOL)
    private var slot = 0
    private var count = 0
    private var zebra: Bitmap? = null
    private var peaking: Bitmap? = null
    private var histogram: List<IntArray>? = null

    suspend fun process(jpeg: ByteArray, options: LiveOptions): LiveFrame? {
        val decoded = decode(jpeg) ?: return null
        val picture = options.look?.let { engine.render(decoded, it) } ?: decoded
        count++
        if (!options.zebra) zebra = null
        if (!options.peaking) peaking = null
        if (!options.histogram) histogram = null
        // Overlays every other frame and the histogram every fourth: the eye doesn't see the
        // difference, the phone does.
        val overlays = (options.zebra || options.peaking) && (count % 2 == 0 || (zebra == null && peaking == null))
        val measureHistogram = options.histogram && (count % 4 == 0 || histogram == null)
        if (overlays || measureHistogram) measure(picture, options, overlays, measureHistogram)
        return LiveFrame(picture, zebra, peaking, histogram)
    }

    private fun decode(jpeg: ByteArray): Bitmap? {
        val reuse = pool[slot]
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inBitmap = reuse
        }
        val bitmap = runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) }.getOrNull()
            ?: runCatching {
                // Size changed (zoom on some bodies): start a fresh buffer.
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inMutable = true })
            }.getOrNull()
            ?: return null
        pool[slot] = bitmap
        slot = (slot + 1) % POOL
        return bitmap
    }

    private fun measure(picture: Bitmap, options: LiveOptions, overlays: Boolean, measureHistogram: Boolean) {
        val small = Bitmap.createScaledBitmap(picture, (picture.width / 2).coerceAtLeast(1), (picture.height / 2).coerceAtLeast(1), true)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== picture) small.recycle()

        val measured = if ((overlays && options.zebra) || measureHistogram) ImageAnalyzer.measure(pixels, options.zebraLevel) else null
        if (overlays) {
            zebra = if (options.zebra && measured != null && measured.clippedCount > 0) {
                // Diagonal stripes over the blown areas, like a camera's zebra.
                val stripes = IntArray(pixels.size) { i ->
                    if (measured.clipped[i] && ((i % w + i / w) / 4) % 2 == 0) ZEBRA else 0
                }
                Bitmap.createBitmap(stripes, w, h, Bitmap.Config.ARGB_8888)
            } else null
            peaking = if (options.peaking) {
                val m = Peaking.mask(CullMath.luma(pixels), w, h, options.peakingSensitivity)
                Bitmap.createBitmap(IntArray(m.size) { if (m[it]) options.peakingColor else 0 }, w, h, Bitmap.Config.ARGB_8888)
            } else null
        }
        if (measureHistogram && measured != null) histogram = listOf(measured.luma, measured.red, measured.green, measured.blue)
    }

    private companion object {
        const val ZEBRA = 0xCCFF3B30.toInt()
        const val POOL = 3
    }
}
