package com.example.revela

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The studio's logo, kept inside the app (files/brand/logo.png) so it survives the photo being moved
 * or deleted from the gallery. Scaled down to [MAX_EDGE]: enough for a page header and a watermark.
 */
object BrandLogo {
    private const val MAX_EDGE = 1024

    fun file(context: Context) = File(context.filesDir, "brand/logo.png")

    /** Copies the picked image in as PNG (transparency kept). Returns its path, or null when it can't be read. */
    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                    .also { if (it !== decoded) decoded.recycle() }
            } else decoded
            val out = file(context)
            out.parentFile?.mkdirs()
            val temp = File(out.parentFile, "logo.png.part")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            if (!temp.renameTo(out)) { temp.copyTo(out, overwrite = true); temp.delete() }
            out.absolutePath
        }.getOrNull()
    }

    suspend fun remove(context: Context) = withContext(Dispatchers.IO) { file(context).delete() }
}
