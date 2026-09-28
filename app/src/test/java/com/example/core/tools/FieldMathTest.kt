package com.example.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldMathTest {
    @Test
    fun flashGuideNumber() {
        // GN 58 at ISO 100, full power: f/5.8 at 10 m. ISO 400 doubles the reach; 1/4 power halves it.
        assertEquals(5.8, FieldMath.apertureFor(58.0, 100, 1, 10.0), 1e-9)
        assertEquals(58.0 * 2 / 8, FieldMath.reachFor(58.0, 400, 1, 8.0), 1e-9)
        assertEquals(58.0 / 2 / 8, FieldMath.reachFor(58.0, 100, 4, 8.0), 1e-9)
        assertEquals(5.6, FieldMath.nearestStop(5.8), 1e-9)
    }

    @Test
    fun depthOfFieldFifty() {
        // 50 mm f/1.8 at 3 m on APS-C (CoC 0.019): about 2.88 m to 3.13 m.
        val dof = FieldMath.depthOfField(50.0, 1.8, 3_000.0)
        assertEquals(2_884.0, dof.nearMm, 2.0)
        assertEquals(3_126.0, dof.farMm, 2.0)
        assertEquals(73_150.0, dof.hyperfocalMm, 5.0)
    }

    @Test
    fun hyperfocalKitWide() {
        // 18 mm f/8: hyperfocal about 2.15 m; focusing there gives half of it to infinity.
        val h = FieldMath.hyperfocalMm(18.0, 8.0)
        assertEquals(2_150.0, h, 5.0)
        val dof = FieldMath.depthOfField(18.0, 8.0, h)
        assertTrue(dof.infinite)
        assertEquals(h / 2, dof.nearMm, 15.0)
    }

    @Test
    fun formats() {
        assertEquals("2,40 m", FieldMath.formatDistance(2_400.0))
        assertEquals("35 cm", FieldMath.formatDistance(350.0))
        assertEquals("f/5,6", FieldMath.formatAperture(5.6))
        assertEquals("f/8", FieldMath.formatAperture(8.0))
    }
}
