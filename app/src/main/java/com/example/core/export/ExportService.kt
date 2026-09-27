package com.example.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.core.data.Settings
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Where the logo goes: 0..8 in reading order (0 top-left, 4 centre, 8 bottom-right). */
object WatermarkLayout {
    /**
     * Logo rectangle on an image. [sizePct] is the logo width as a share of the image's shorter side,
     * [marginPct] the gap to the edges, same reference, so it looks the same on vertical and horizontal photos.
     */
    fun place(imageW: Int, imageH: Int, logoW: Int, logoH: Int, position: Int, sizePct: Int, marginPct: Int): Rect {
        val ref = minOf(imageW, imageH)
        val w = max(1, ref * sizePct.coerceIn(1, 100) / 100)
        val h = max(1, (w.toLong() * logoH / max(1, logoW)).toInt())
        val m = ref * marginPct.coerceIn(0, 30) / 100
        val col = position.coerceIn(0, 8) % 3
        val row = position.coerceIn(0, 8) / 3
        val left = when (col) {
            0 -> m
            1 -> (imageW - w) / 2
            else -> imageW - w - m
        }
        val top = when (row) {
            0 -> m
            1 -> (imageH - h) / 2
            else -> imageH - h - m
        }
        return Rect(left, top, left + w, top + h)
    }

    /** Scale that brings the long edge down to [maxEdge] (0 = keep original size). */
    fun scaleFor(width: Int, height: Int, maxEdge: Int): Float {
        if (maxEdge <= 0) return 1f
        val longEdge = max(width, height)
        return if (longEdge <= maxEdge) 1f else maxEdge.toFloat() / longEdge
    }
}

/**
 * Makes the light copy used for WhatsApp and similar: resized, re-compressed, optionally watermarked.
 * Works on a copy in the app cache; the saved photo is never modified.
 */
class ExportService(private val context: Context) {

    private val dir get() = File(context.cacheDir, "export").apply { mkdirs() }

    fun exportLight(sourceUri: String, name: String, settings: Settings): File? = runCatching {
        val uri = Uri.parse(sourceUri)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null

        // Decode at the nearest power of two above the target, then scale exactly (saves memory on 15 MP files).
        val target = settings.lightShareSize
        var sample = 1
        if (target > 0) while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inMutable = true
            })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = WatermarkLayout.scaleFor(bitmap.width, bitmap.height, target)
        if (rotation != 0 || scale < 1f) {
            val m = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation.toFloat())
            }
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (transformed !== bitmap) bitmap.recycle()
            bitmap = transformed.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== transformed) transformed.recycle() }
        }

        if (settings.watermarkEnabled) drawWatermark(bitmap, settings)

        val out = File(dir, "${name}_leve.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, settings.lightShareQuality.coerceIn(50, 100), it) }
        bitmap.recycle()
        out
    }.getOrNull()

    private fun drawWatermark(bitmap: Bitmap, settings: Settings) {
        val path = settings.watermarkPath ?: return
        val logo = BitmapFactory.decodeFile(path) ?: return
        val rect = WatermarkLayout.place(
            bitmap.width, bitmap.height, logo.width, logo.height,
            settings.watermarkPosition, settings.watermarkSizePct, settings.watermarkMarginPct,
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (settings.watermarkOpacity.coerceIn(0, 100) * 2.55f).roundToInt()
        }
        Canvas(bitmap).drawBitmap(logo, null, rect, paint)
        logo.recycle()
    }

    /** Copies a picked logo into the app so it survives the original being moved or deleted. */
    fun importLogo(source: Uri): String? = runCatching {
        val file = File(context.filesDir, "watermark.png")
        context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth > 0) file.path else null.also { file.delete() }
    }.getOrNull()

    fun clearOldExports() {
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
    }
}
