package com.example.core.church

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The name mark on the pictures of who cannot download: drawn over the whole frame, at both sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChurchMarkTest {

    private fun marked(w: Int, h: Int, name: String): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawColor(Color.rgb(40, 70, 90))
        ChurchDelivery.mark(b, "Bia · Recepção", "24/11 19:42")
        File("build/screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return b
    }

    /** Share of the frame's columns, in a band across the middle, that the mark touches. */
    private fun coverage(b: Bitmap): Float {
        val base = Color.rgb(40, 70, 90)
        val touched = (0 until b.width).count { x -> (b.height / 3 until 2 * b.height / 3).any { y -> b.getPixel(x, y) != base } }
        return touched.toFloat() / b.width
    }

    @Test
    fun theMarkCoversThumbnailAndPreview() {
        assertTrue(coverage(marked(480, 320, "marca_miniatura")) > 0.6f)
        assertTrue(coverage(marked(1600, 1067, "marca_previa")) > 0.6f)
    }
}
