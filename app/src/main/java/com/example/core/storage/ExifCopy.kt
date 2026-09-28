package com.example.core.storage

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Copies to rendered JPGs (edited copy, light JPG) the camera data Lightroom and clients look for,
 * and the GPS position when there is one. Orientation is not copied: the pixels are already upright.
 */
object ExifCopy {
    private val TAGS = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_FLASH, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
    )

    fun apply(context: Context, target: File, sourceUri: String?, lat: Double?, lon: Double?) {
        runCatching {
            val out = ExifInterface(target.absolutePath)
            sourceUri?.let { src ->
                context.contentResolver.openInputStream(Uri.parse(src))?.use { input ->
                    val source = ExifInterface(input)
                    TAGS.forEach { tag -> source.getAttribute(tag)?.let { out.setAttribute(tag, it) } }
                }
            }
            if (lat != null && lon != null) out.setLatLong(lat, lon)
            out.setAttribute(ExifInterface.TAG_SOFTWARE, "Jr_Link")
            out.saveAttributes()
        }
    }
}
