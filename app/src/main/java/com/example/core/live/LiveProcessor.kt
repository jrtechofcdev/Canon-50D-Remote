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

    suspend fun process(jpeg: ByteArray, options: LiveOptions): LiveFrame? {
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val picture = options.look?.let { engine.render(decoded, it).also { decoded.recycle() } } ?: decoded
        if (!options.zebra && !options.peaking && !options.histogram) return LiveFrame(picture, null, null, null)

        val small = Bitmap.createScaledBitmap(picture, (picture.width / 2).coerceAtLeast(1), (picture.height / 2).coerceAtLeast(1), true)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== picture) small.recycle()

        val measured = if (options.zebra || options.histogram) ImageAnalyzer.measure(pixels, options.zebraLevel) else null
        val zebra = if (options.zebra && measured != null && measured.clippedCount > 0) {
            // Diagonal stripes over the blown areas, like a camera's zebra.
            val stripes = IntArray(pixels.size) { i ->
                if (measured.clipped[i] && ((i % w + i / w) / 4) % 2 == 0) ZEBRA else 0
            }
            Bitmap.createBitmap(stripes, w, h, Bitmap.Config.ARGB_8888)
        } else null
        val peaking = if (options.peaking) {
            val m = Peaking.mask(CullMath.luma(pixels), w, h, options.peakingSensitivity)
            Bitmap.createBitmap(IntArray(m.size) { if (m[it]) options.peakingColor else 0 }, w, h, Bitmap.Config.ARGB_8888)
        } else null
        val histogram = if (options.histogram && measured != null) listOf(measured.luma, measured.red, measured.green, measured.blue) else null
        return LiveFrame(picture, zebra, peaking, histogram)
    }

    private companion object {
        const val ZEBRA = 0xCCFF3B30.toInt()
    }
}
