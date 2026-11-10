package com.example.core.render

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.core.data.Photo

/** Whether a photo, as it is shown (camera orientation and crop applied), stands up or lies down. */
object PhotoShape {

    /** True for a vertical photo, false for a horizontal one, null when the file can't be read. */
    fun isPortrait(context: Context, photo: Photo): Boolean? = runCatching {
        val uri = Uri.parse(photo.displayUri ?: return null)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        shape(bounds.outWidth, bounds.outHeight, rotation, CropSpec.decode(photo.crop))
    }.getOrNull()

    /** The pure part of [isPortrait], for tests: pixel size, EXIF rotation and crop. */
    fun shape(width: Int, height: Int, exifRotation: Int, crop: CropSpec?): Boolean {
        var w = width.toFloat()
        var h = height.toFloat()
        if (exifRotation % 180 != 0) { val t = w; w = h; h = t }
        if (crop != null) {
            if (crop.quarterTurns % 2 != 0) { val t = w; w = h; h = t }
            w *= crop.width
            h *= crop.height
        }
        return h > w
    }

    /**
     * The crop turned a quarter clockwise, keeping the same framing: "Girar" in the viewer, for
     * photos the camera saved lying down (camera's auto-rotate off) or to turn one on purpose.
     */
    fun turnedClockwise(crop: CropSpec?): CropSpec {
        val c = crop ?: CropSpec()
        // A point (x, y) of the picture ends up at (1 - y, x) after a clockwise quarter turn.
        return c.copy(
            left = 1f - c.bottom,
            top = c.left,
            right = 1f - c.top,
            bottom = c.right,
            quarterTurns = (c.quarterTurns + 1) % 4,
            targetW = c.targetH,
            targetH = c.targetW,
        )
    }
}
