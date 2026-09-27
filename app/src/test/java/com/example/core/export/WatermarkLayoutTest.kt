package com.example.core.export

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatermarkLayoutTest {

    @Test
    fun bottomRightUsesShorterSideForSizeAndMargin() {
        // 3000×2000 photo, 400×200 logo, 20% size, 3% margin → logo 400 wide, margin 60.
        val r = WatermarkLayout.place(3000, 2000, 400, 200, position = 8, sizePct = 20, marginPct = 3)
        assertEquals(400, r.width())
        assertEquals(200, r.height())
        assertEquals(3000 - 60, r.right)
        assertEquals(2000 - 60, r.bottom)
    }

    @Test
    fun centreAndTopLeft() {
        val c = WatermarkLayout.place(1000, 1000, 100, 100, position = 4, sizePct = 10, marginPct = 0)
        assertEquals(450, c.left)
        assertEquals(450, c.top)
        val tl = WatermarkLayout.place(1000, 500, 100, 50, position = 0, sizePct = 20, marginPct = 2)
        assertEquals(10, tl.left)
        assertEquals(10, tl.top)
    }

    @Test
    fun scaleOnlyShrinks() {
        assertEquals(0.5f, WatermarkLayout.scaleFor(4000, 3000, 2000), 0.0001f)
        assertEquals(1f, WatermarkLayout.scaleFor(1000, 800, 2048), 0.0001f)
        assertEquals(1f, WatermarkLayout.scaleFor(4000, 3000, 0), 0.0001f)
    }
}
