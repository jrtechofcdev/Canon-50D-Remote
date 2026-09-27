package com.example.core.camera

import com.example.core.ptp.EosValues

/**
 * Bracketing math. Canon stores exposure in eighths of a stop with thirds rounded to 3 and 5,
 * so values are converted to thirds, shifted, and converted back rather than added as raw codes.
 */
object Bracket {
    /** Offsets in thirds of a stop, in the camera's own AEB order: 0, −, +, −−, ++ … */
    fun offsets(shots: Int, stepThirds: Int): List<Int> {
        val n = shots.coerceIn(3, 7).let { if (it % 2 == 0) it + 1 else it }
        return buildList {
            add(0)
            for (k in 1..n / 2) {
                add(-k * stepThirds)
                add(k * stepThirds)
            }
        }
    }

    /** Exposure compensation raw value (signed byte in the low 8 bits) shifted by [thirds], limited to ±2 EV. */
    fun compensation(base: Int, thirds: Int): Int {
        val total = (EosValues.codeToThirds(base.toByte().toInt()) + thirds).coerceIn(-6, 6)
        return EosValues.thirdsToCode(total) and 0xFF
    }

    /** Shutter code shifted by [thirds] of exposure: positive = brighter = slower shutter. */
    fun shutter(base: Int, thirds: Int): Int {
        val fromOneSecond = EosValues.codeToThirds(base - ONE_SECOND) - thirds
        val code = (ONE_SECOND + EosValues.thirdsToCode(fromOneSecond)).coerceIn(FASTEST_30S, SLOWEST_8000)
        return code
    }

    private const val ONE_SECOND = 0x38
    private const val FASTEST_30S = 0x10
    private const val SLOWEST_8000 = 0xA0
}
