package com.example.core.preset

import java.util.Locale

data class CurvePoint(val x: Int, val y: Int)

/**
 * A develop preset, stored with Adobe's own setting names (the `crs:` keys of Camera Raw / Lightroom),
 * so anything imported round-trips to XMP untouched, including settings the app does not render.
 */
data class PresetSettings(
    val values: Map<String, String> = emptyMap(),
    val curves: Map<String, List<CurvePoint>> = emptyMap(),
) {
    fun num(key: String, default: Double = 0.0): Double =
        values[key]?.trim()?.removePrefix("+")?.toDoubleOrNull() ?: default

    fun bool(key: String): Boolean = values[key].equals("True", ignoreCase = true)

    fun with(key: String, value: Double): PresetSettings = copy(values = values + (key to format(key, value)))

    fun withText(key: String, value: String?): PresetSettings =
        copy(values = if (value == null) values - key else values + (key to value))

    fun withCurve(key: String, points: List<CurvePoint>?): PresetSettings =
        copy(curves = if (points == null) curves - key else curves + (key to points))

    fun curve(key: String): List<CurvePoint> = curves[key] ?: IDENTITY_CURVE

    /** Settings that actually change the picture (meta keys like UUID or version are left out). */
    val adjustmentKeys: Set<String> get() = values.keys.filterNot { it in META_KEYS }.toSet()

    /** Line-based text used for the database: `key=value` and `curve:Key=x,y;x,y`. */
    fun encode(): String = buildString {
        values.toSortedMap().forEach { (k, v) -> append(k).append('=').append(v.replace("\n", " ")).append('\n') }
        curves.toSortedMap().forEach { (k, pts) ->
            append("curve:").append(k).append('=').append(pts.joinToString(";") { "${it.x},${it.y}" }).append('\n')
        }
    }

    companion object {
        val IDENTITY_CURVE = listOf(CurvePoint(0, 0), CurvePoint(255, 255))

        fun decode(text: String): PresetSettings {
            val values = mutableMapOf<String, String>()
            val curves = mutableMapOf<String, List<CurvePoint>>()
            text.lineSequence().filter { '=' in it }.forEach { line ->
                val key = line.substringBefore('=')
                val value = line.substringAfter('=')
                if (key.startsWith("curve:")) {
                    curves[key.removePrefix("curve:")] = value.split(';').mapNotNull { p ->
                        val parts = p.split(',')
                        val x = parts.getOrNull(0)?.trim()?.toIntOrNull()
                        val y = parts.getOrNull(1)?.trim()?.toIntOrNull()
                        if (x != null && y != null) CurvePoint(x, y) else null
                    }
                } else {
                    values[key] = value
                }
            }
            return PresetSettings(values, curves)
        }

        /** Adobe writes exposure with two decimals and a sign, and most sliders as signed integers. */
        fun format(key: String, value: Double): String = when (key) {
            in DECIMAL_KEYS -> String.format(Locale.US, "%+.2f", value)
            in UNSIGNED_KEYS -> String.format(Locale.US, "%d", Math.round(value))
            else -> Math.round(value).let { if (it > 0) "+$it" else "$it" }
        }

        private val DECIMAL_KEYS = setOf("Exposure2012", "SharpenRadius")
        private val UNSIGNED_KEYS = setOf(
            "Sharpness", "SharpenDetail", "SharpenEdgeMasking", "LuminanceSmoothing", "ColorNoiseReduction",
            "GrainAmount", "GrainSize", "GrainFrequency", "Temperature", "ColorGradeBlending",
            "ParametricShadowSplit", "ParametricMidtoneSplit", "ParametricHighlightSplit",
            "PostCropVignetteMidpoint", "PostCropVignetteFeather",
            "ColorGradeShadowHue", "ColorGradeMidtoneHue", "ColorGradeHighlightHue", "ColorGradeGlobalHue",
            "ColorGradeShadowSat", "ColorGradeMidtoneSat", "ColorGradeHighlightSat", "ColorGradeGlobalSat",
            "SplitToningShadowHue", "SplitToningHighlightHue", "SplitToningShadowSaturation", "SplitToningHighlightSaturation",
        )

        val META_KEYS = setOf(
            "PresetType", "Cluster", "UUID", "SupportsAmount", "SupportsAmount2", "SupportsColor", "SupportsMonochrome",
            "SupportsHighDynamicRange", "SupportsNormalDynamicRange", "SupportsSceneReferred", "SupportsOutputReferred",
            "RequiresRGBTables", "CameraModelRestriction", "Copyright", "ContactInfo", "Version", "HasSettings",
            "ProcessVersion", "HasCrop", "AlreadyApplied", "ShowInQuickActions", "SortName", "ShortName",
        )

        /** The eight hue bands of Lightroom's Color Mixer, in its order. */
        val HSL_COLORS = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")

        val CURVE_KEYS = listOf("ToneCurvePV2012", "ToneCurvePV2012Red", "ToneCurvePV2012Green", "ToneCurvePV2012Blue")
    }
}

data class Preset(
    val id: Long = 0,
    val name: String,
    val group: String = "JR C50D Sync",
    val settings: PresetSettings = PresetSettings(),
    /** Where it came from: "dng", "xmp", "lrtemplate" or "criado". */
    val source: String = "criado",
)
