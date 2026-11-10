package com.example.core.delivery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The preview mark of Fotografia Express: the logo large in the middle and "PRÉVIA · name" repeated
 * diagonally across the whole frame, so a screenshot of the portal is no use as the final photo.
 */
object ExpressPreview {
    /** Long edge of a preview: enough to choose on a phone, not enough to print. */
    const val EDGE = 1280

    fun mark(bitmap: Bitmap, logo: Bitmap?, photographer: String, opacityPct: Int) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val alpha = (opacityPct.coerceIn(20, 80) * 2.55f).roundToInt()

        // Repeated diagonal text over everything.
        val text = "PRÉVIA · $photographer"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            this.alpha = (alpha * 0.7f).roundToInt()
            textSize = minOf(w, h) / 14f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 0f, 0x66000000)
        }
        val step = paint.measureText(text) + paint.textSize * 2
        val lineGap = paint.textSize * 3.2f
        val diag = sqrt(w * w + h * h)
        canvas.save()
        canvas.rotate(-30f, w / 2, h / 2)
        var y = (h - diag) / 2
        var row = 0
        while (y < (h + diag) / 2) {
            var x = (w - diag) / 2 - (row % 2) * step / 2
            while (x < (w + diag) / 2) {
                canvas.drawText(text, x, y, paint)
                x += step
            }
            y += lineGap
            row++
        }
        canvas.restore()

        // The logo in the middle, over the subject.
        logo?.let {
            val lw = minOf(w, h) * 0.6f
            val lh = lw * it.height / max(1, it.width)
            val rect = Rect(((w - lw) / 2).toInt(), ((h - lh) / 2).toInt(), ((w + lw) / 2).toInt(), ((h + lh) / 2).toInt())
            canvas.drawBitmap(it, null, rect, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
        }
    }
}
