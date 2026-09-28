package com.example.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/** Decodes photos upright (EXIF orientation applied) and no larger than needed. */
object BitmapLoader {
    /**
     * Long edge at most [maxEdge] (0 = full size). Decodes at the nearest power-of-two reduction first,
     * which keeps a 15 MP JPEG from ever being fully decoded just for a preview.
     */
    fun load(context: Context, uri: Uri, maxEdge: Int): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        if (maxEdge > 0) while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = if (maxEdge > 0) minOf(1f, maxEdge.toFloat() / max(decoded.width, decoded.height)) else 1f
        if (rotation == 0 && scale >= 1f) return decoded
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (out !== decoded) decoded.recycle()
        out
    }.getOrNull()
}
