package com.example.core.ptp

/** Converts Canon EOS property codes into the labels a photographer reads on the camera's top LCD. */
object EosValues {

    private val aperture = mapOf(
        0x08 to "1.0", 0x0B to "1.1", 0x0C to "1.2", 0x0D to "1.2", 0x10 to "1.4", 0x13 to "1.6",
        0x14 to "1.8", 0x15 to "1.8", 0x18 to "2.0", 0x1B to "2.2", 0x1C to "2.5", 0x1D to "2.5",
        0x20 to "2.8", 0x23 to "3.2", 0x24 to "3.5", 0x25 to "3.5", 0x28 to "4.0", 0x2B to "4.5",
        0x2C to "4.5", 0x2D to "5.0", 0x30 to "5.6", 0x33 to "6.3", 0x34 to "6.7", 0x35 to "7.1",
        0x38 to "8.0", 0x3B to "9.0", 0x3C to "9.5", 0x3D to "10", 0x40 to "11", 0x43 to "13",
        0x44 to "13", 0x45 to "14", 0x48 to "16", 0x4B to "18", 0x4C to "19", 0x4D to "20",
        0x50 to "22", 0x53 to "25", 0x54 to "27", 0x55 to "29", 0x58 to "32", 0x5B to "36",
        0x5C to "38", 0x5D to "40", 0x60 to "45",
    )

    private val shutter = mapOf(
        0x0C to "Bulb", 0x10 to "30\"", 0x13 to "25\"", 0x14 to "20\"", 0x15 to "20\"", 0x18 to "15\"",
        0x1B to "13\"", 0x1C to "10\"", 0x1D to "10\"", 0x20 to "8\"", 0x23 to "6\"", 0x24 to "6\"",
        0x25 to "5\"", 0x28 to "4\"", 0x2B to "3\"2", 0x2C to "3\"", 0x2D to "2\"5", 0x30 to "2\"",
        0x33 to "1\"6", 0x34 to "1\"5", 0x35 to "1\"3", 0x38 to "1\"", 0x3B to "0\"8", 0x3C to "0\"7",
        0x3D to "0\"6", 0x40 to "0\"5", 0x43 to "0\"4", 0x44 to "0\"3", 0x45 to "0\"3", 0x48 to "1/4",
        0x4B to "1/5", 0x4C to "1/6", 0x4D to "1/6", 0x50 to "1/8", 0x53 to "1/10", 0x54 to "1/10",
        0x55 to "1/13", 0x58 to "1/15", 0x5B to "1/20", 0x5C to "1/20", 0x5D to "1/25", 0x60 to "1/30",
        0x63 to "1/40", 0x64 to "1/45", 0x65 to "1/50", 0x68 to "1/60", 0x6B to "1/80", 0x6C to "1/90",
        0x6D to "1/100", 0x70 to "1/125", 0x73 to "1/160", 0x74 to "1/180", 0x75 to "1/200",
        0x78 to "1/250", 0x7B to "1/320", 0x7C to "1/350", 0x7D to "1/400", 0x80 to "1/500",
        0x83 to "1/640", 0x84 to "1/750", 0x85 to "1/800", 0x88 to "1/1000", 0x8B to "1/1250",
        0x8C to "1/1500", 0x8D to "1/1600", 0x90 to "1/2000", 0x93 to "1/2500", 0x94 to "1/3000",
        0x95 to "1/3200", 0x98 to "1/4000", 0x9B to "1/5000", 0x9C to "1/6000", 0x9D to "1/6400",
        0xA0 to "1/8000",
    )

    private val iso = mapOf(
        0x00 to "Auto", 0x40 to "50", 0x48 to "100", 0x4B to "125", 0x4D to "160", 0x50 to "200",
        0x53 to "250", 0x55 to "320", 0x58 to "400", 0x5B to "500", 0x5D to "640", 0x60 to "800",
        0x63 to "1000", 0x65 to "1250", 0x68 to "1600", 0x6B to "2000", 0x6D to "2500", 0x70 to "3200",
        0x73 to "4000", 0x75 to "5000", 0x78 to "6400", 0x80 to "H1 12800", 0x88 to "H2 25600",
    )

    private val whiteBalance = mapOf(
        0 to "Auto", 1 to "Luz do dia", 2 to "Nublado", 3 to "Tungstênio", 4 to "Fluorescente",
        5 to "Flash", 6 to "Personalizado", 8 to "Sombra", 9 to "Kelvin",
    )

    private val shootingMode = mapOf(
        0 to "P", 1 to "Tv", 2 to "Av", 3 to "M", 4 to "B", 5 to "A-DEP", 7 to "C", 8 to "CA",
        9 to "Auto", 10 to "Auto", 11 to "Retrato", 12 to "Paisagem", 13 to "Macro", 14 to "Esporte",
        15 to "Retrato noturno", 16 to "Sem flash",
    )

    // Canon reports battery in coarse steps; codes as mapped by libgphoto2.
    private val battery = mapOf(0 to "Baixa", 5 to "25%", 1 to "50%", 4 to "75%", 2 to "100%", 3 to "Cheia")

    fun batteryLabel(value: Int): String = battery[value] ?: "--"

    fun batteryIsLow(value: Int): Boolean = value == 0 || value == 5

    fun label(prop: Int, value: Int): String = when (prop) {
        EosProp.APERTURE -> aperture[value]?.let { "f/$it" } ?: "--"
        EosProp.SHUTTER_SPEED -> shutter[value] ?: "--"
        EosProp.ISO -> iso[value] ?: "--"
        EosProp.EXPOSURE_COMPENSATION -> exposureCompensation(value)
        EosProp.WHITE_BALANCE -> whiteBalance[value] ?: "--"
        EosProp.SHOOTING_MODE -> shootingMode[value] ?: "--"
        EosProp.BATTERY_POWER -> batteryLabel(value)
        else -> value.toString()
    }

    /** Values sent by the camera are signed eighths of a stop stored in the low byte (0x03 = +1/3, 0xFB = -2/3). */
    fun exposureCompensation(raw: Int): String {
        val v = raw.toByte().toInt()
        if (v == 0) return "0"
        val sign = if (v > 0) "+" else "−"
        val abs = kotlin.math.abs(v)
        val whole = abs / 8
        val fraction = when (abs % 8) {
            3 -> "1/3"
            4 -> "1/2"
            5 -> "2/3"
            else -> ""
        }
        val body = when {
            whole == 0 -> fraction
            fraction.isEmpty() -> "$whole"
            else -> "$whole $fraction"
        }
        return sign + body
    }

    /** Canon code offset for a step in thirds of a stop: 1/3 = 3, 2/3 = 5, 1 = 8 (eighths with Canon's rounding). */
    fun thirdsToCode(thirds: Int): Int {
        val sign = if (thirds < 0) -1 else 1
        val a = kotlin.math.abs(thirds)
        return sign * ((a / 3) * 8 + intArrayOf(0, 3, 5)[a % 3])
    }

    /** Inverse of [thirdsToCode]; half-stop codes (4) round to 2/3. */
    fun codeToThirds(code: Int): Int {
        val sign = if (code < 0) -1 else 1
        val a = kotlin.math.abs(code)
        val rem = when (a % 8) {
            0 -> 0
            1, 2, 3 -> 1
            4, 5 -> 2
            else -> 3
        }
        return sign * ((a / 8) * 3 + rem)
    }

    /** Shutter speed in seconds for a Tv code (used to wait long enough during bracketing). */
    fun shutterSeconds(code: Int): Double = Math.pow(2.0, (0x38 - code) / 8.0)

    fun isKnownShutter(code: Int) = code in shutter && code != 0x0C

    /** Default choices shown before the camera reports its own list through AvailListChanged. */
    fun defaultValues(prop: Int): List<Int> = when (prop) {
        EosProp.APERTURE -> aperture.keys.sorted().distinctBy { aperture[it] }
        EosProp.SHUTTER_SPEED -> shutter.keys.sorted().distinctBy { shutter[it] }
        EosProp.ISO -> listOf(0x00, 0x48, 0x50, 0x58, 0x60, 0x68, 0x70)
        EosProp.WHITE_BALANCE -> listOf(0, 1, 8, 2, 3, 4, 5, 6)
        EosProp.EXPOSURE_COMPENSATION -> listOf(0xF0, 0xF3, 0xF5, 0xF8, 0xFB, 0xFD, 0, 3, 5, 8, 11, 13, 16)
        else -> emptyList()
    }
}
