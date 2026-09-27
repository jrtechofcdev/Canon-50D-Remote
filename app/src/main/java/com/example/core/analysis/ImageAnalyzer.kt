package com.example.core.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/** Histogram bins (64 per channel) plus which pixels are clipped. */
class Analysis(
    val luma: IntArray,
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    /** Same size as the analysed image: red where clipped, transparent elsewhere. Null when nothing clips. */
    val clippedMask: Bitmap?,
    val clippedPercent: Float,
)

/**
 * Decodes a reduced copy (long edge ≈ 1024 px, a few MB at most, safe on a 4 GB phone) and measures it.
 * A pixel counts as blown when any channel reaches [threshold], the same rule as the camera's highlight alert.
 */
object ImageAnalyzer {
    private const val TARGET_EDGE = 1024
    private const val MASK_COLOR = 0xFFFF3B30.toInt()

    fun analyze(context: Context, uri: String, threshold: Int): Analysis? = runCatching {
        val bitmap = decodeReduced(context, Uri.parse(uri)) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val result = measure(pixels, threshold)
        val mask = if (result.clippedCount > 0) {
            val maskPixels = IntArray(pixels.size) { if (result.clipped[it]) MASK_COLOR else 0 }
            Bitmap.createBitmap(maskPixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        } else null
        bitmap.recycle()
        Analysis(result.luma, result.red, result.green, result.blue, mask, 100f * result.clippedCount / pixels.size)
    }.getOrNull()

    class Measurement(
        val luma: IntArray,
        val red: IntArray,
        val green: IntArray,
        val blue: IntArray,
        val clipped: BooleanArray,
        val clippedCount: Int,
    )

    /** Pure pixel pass, kept separate so it can be unit tested without Android bitmaps. */
    fun measure(pixels: IntArray, threshold: Int): Measurement {
        val luma = IntArray(64)
        val red = IntArray(64)
        val green = IntArray(64)
        val blue = IntArray(64)
        val clipped = BooleanArray(pixels.size)
        var count = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            red[r shr 2]++
            green[g shr 2]++
            blue[b shr 2]++
            luma[((r * 299 + g * 587 + b * 114) / 1000) shr 2]++
            if (r >= threshold || g >= threshold || b >= threshold) {
                clipped[i] = true
                count++
            }
        }
        return Measurement(luma, red, green, blue, clipped, count)
    }

    private fun decodeReduced(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // The viewer shows the photo upright (EXIF orientation), so the mask has to be rotated the same way.
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }
}
