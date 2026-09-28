package com.example.core.render

import android.graphics.Bitmap
import com.example.core.preset.PresetSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetEngineTest {
    @Test
    fun fallsBackToCpuWhenThereIsNoGpu() = runBlocking {
        val engine = PresetEngine()
        val src = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF808080.toInt()) }
        val out = engine.render(src, RenderParams.from(PresetSettings().with("Exposure2012", 1.0)))
        assertNotSame(src, out)
        assertEquals(8, out.width)
        val grey = out.getPixel(4, 4) and 0xFF
        assert(grey > 0x80) { "exposure +1 should brighten, got $grey" }
        engine.release()
    }
}
