package com.example.core.tools

import kotlin.math.sqrt

/** Speedlites the photographer uses, with the guide number in metres at ISO 100, head at 105 mm. */
enum class Flash(val label: String, val guideNumber: Double) {
    YN560IV("Yongnuo YN560 IV", 58.0),
    YN560III("Yongnuo YN560 III", 58.0),
    YN565EX("Yongnuo YN565EX", 58.0),
    YN568EX("Yongnuo YN568EX", 58.0),
    YN460("Yongnuo YN460", 38.0),
}

/** Lenses of the kit on the 50D. */
enum class Lens(val label: String, val minFocal: Int, val maxFocal: Int, val widest: Double) {
    Fifty("50mm f/1.8", 50, 50, 1.8),
    Kit("18-55mm", 18, 55, 3.5),
}

data class DepthOfField(val nearMm: Double, val farMm: Double, val hyperfocalMm: Double) {
    val infinite: Boolean get() = farMm.isInfinite()
    val totalMm: Double get() = farMm - nearMm
}

object FieldMath {
    /** Circle of confusion for the 50D's APS-C sensor (1.6× crop). */
    const val COC_50D_MM = 0.019

    val POWERS = listOf(1, 2, 4, 8, 16, 32, 64, 128)
    val ISOS = listOf(100, 200, 400, 800, 1600, 3200)
    val APERTURES = listOf(1.8, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0)

    /** Guide number at [iso] and power 1/[powerDivisor]: GN × √(ISO/100) × √(potência). */
    fun effectiveGuide(gn: Double, iso: Int, powerDivisor: Int): Double =
        gn * sqrt(iso / 100.0) * sqrt(1.0 / powerDivisor)

    /** f-number that exposes a subject at [distanceM] correctly (direct flash, no modifier). */
    fun apertureFor(gn: Double, iso: Int, powerDivisor: Int, distanceM: Double): Double =
        effectiveGuide(gn, iso, powerDivisor) / distanceM

    /** Farthest subject the flash reaches at [aperture]. */
    fun reachFor(gn: Double, iso: Int, powerDivisor: Int, aperture: Double): Double =
        effectiveGuide(gn, iso, powerDivisor) / aperture

    /** Nearest standard f-stop (third stops) to a computed value, for display. */
    fun nearestStop(value: Double): Double =
        THIRD_STOPS.minBy { kotlin.math.abs(kotlin.math.ln(it) - kotlin.math.ln(value)) }

    private val THIRD_STOPS = listOf(
        1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0,
        9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0,
    )

    fun hyperfocalMm(focalMm: Double, aperture: Double, coc: Double = COC_50D_MM): Double =
        focalMm * focalMm / (aperture * coc) + focalMm

    fun depthOfField(focalMm: Double, aperture: Double, distanceMm: Double, coc: Double = COC_50D_MM): DepthOfField {
        val h = hyperfocalMm(focalMm, aperture, coc)
        val near = distanceMm * (h - focalMm) / (h + distanceMm - 2 * focalMm)
        val far = if (distanceMm >= h) Double.POSITIVE_INFINITY else distanceMm * (h - focalMm) / (h - distanceMm)
        return DepthOfField(near, far, h)
    }

    /** "2,4 m", "35 cm" or "∞", in Brazilian format. */
    fun formatDistance(mm: Double): String = when {
        mm.isInfinite() -> "∞"
        mm < 1_000 -> "%.0f cm".format(mm / 10)
        mm < 10_000 -> "%.2f m".format(mm / 1_000).replace('.', ',')
        else -> "%.1f m".format(mm / 1_000).replace('.', ',')
    }

    fun formatAperture(n: Double): String = "f/" + (if (n % 1.0 == 0.0) "%.0f".format(n) else "%.1f".format(n)).replace('.', ',')
}
