package com.example.core.preset

/**
 * The "Pack Externo" installed on first run: one preset per real shooting situation.
 * Starting points tuned for JPEGs from a Canon 50D; open any of them in the editor to adjust.
 */
object StarterPresets {
    const val GROUP = "Pack Externo"

    private fun preset(name: String, vararg values: Pair<String, Number>, curve: List<CurvePoint>? = null, text: Map<String, String> = emptyMap()): Preset {
        var s = PresetSettings()
        values.forEach { (k, v) -> s = s.with(k, v.toDouble()) }
        text.forEach { (k, v) -> s = s.withText(k, v) }
        if (curve != null) s = s.withCurve("ToneCurvePV2012", curve)
        return Preset(name = name, group = GROUP, settings = s, source = "jr_link")
    }

    private val softS = listOf(CurvePoint(0, 0), CurvePoint(64, 58), CurvePoint(128, 128), CurvePoint(192, 198), CurvePoint(255, 255))
    private val fadedS = listOf(CurvePoint(0, 18), CurvePoint(64, 62), CurvePoint(128, 130), CurvePoint(192, 196), CurvePoint(255, 248))

    val all: List<Preset> = listOf(
        preset(
            "01 Outdoor Natural",
            "Exposure2012" to 0.1, "Contrast2012" to 8, "Highlights2012" to -25, "Shadows2012" to 20,
            "Vibrance" to 12, "Saturation" to 0, "Clarity2012" to 5,
            "SaturationAdjustmentOrange" to -5, "LuminanceAdjustmentOrange" to 8,
            curve = softS,
        ),
        preset(
            "02 Outdoor Vibrant",
            "Contrast2012" to 15, "Highlights2012" to -30, "Shadows2012" to 25, "Whites2012" to 8, "Blacks2012" to -8,
            "Vibrance" to 30, "Saturation" to 8, "Clarity2012" to 10, "Dehaze" to 5,
            "SaturationAdjustmentBlue" to 15, "LuminanceAdjustmentBlue" to -10, "SaturationAdjustmentOrange" to -8,
            curve = softS,
        ),
        preset(
            "03 Green Control",
            "Highlights2012" to -20, "Shadows2012" to 15, "Vibrance" to 10,
            "HueAdjustmentGreen" to 15, "SaturationAdjustmentGreen" to -30, "LuminanceAdjustmentGreen" to -10,
            "HueAdjustmentYellow" to -10, "SaturationAdjustmentYellow" to -20,
            "LuminanceAdjustmentOrange" to 10,
        ),
        preset(
            "04 Golden Hour",
            "IncrementalTemperature" to 18, "IncrementalTint" to 4, "Exposure2012" to 0.05,
            "Contrast2012" to 5, "Highlights2012" to -35, "Shadows2012" to 25, "Vibrance" to 15,
            "SaturationAdjustmentOrange" to 5, "LuminanceAdjustmentOrange" to 10,
            "ColorGradeHighlightHue" to 42, "ColorGradeHighlightSat" to 18,
            "ColorGradeShadowHue" to 30, "ColorGradeShadowSat" to 8,
            curve = fadedS,
        ),
        preset(
            "05 Hard Sun",
            "Exposure2012" to -0.15, "Contrast2012" to -10, "Highlights2012" to -70, "Shadows2012" to 45,
            "Whites2012" to -15, "Blacks2012" to 10, "Vibrance" to 8, "Clarity2012" to -5, "Dehaze" to -5,
            "LuminanceAdjustmentOrange" to 12,
        ),
        preset(
            "06 Cloudy Day",
            "IncrementalTemperature" to 10, "Exposure2012" to 0.2, "Contrast2012" to 20, "Highlights2012" to -15,
            "Shadows2012" to 10, "Whites2012" to 12, "Blacks2012" to -10, "Vibrance" to 25, "Clarity2012" to 12,
            "Dehaze" to 8,
            curve = softS,
        ),
        preset(
            "07 Portrait Skin",
            "IncrementalTemperature" to 5, "Exposure2012" to 0.1, "Contrast2012" to -5, "Highlights2012" to -30,
            "Shadows2012" to 20, "Vibrance" to 5, "Texture" to -15, "Clarity2012" to -10,
            "HueAdjustmentOrange" to -4, "SaturationAdjustmentOrange" to -10, "LuminanceAdjustmentOrange" to 15,
            "SaturationAdjustmentRed" to -8, "LuminanceAdjustmentRed" to 5,
        ),
        preset(
            "08 Flash Event",
            "IncrementalTemperature" to 6, "Exposure2012" to -0.1, "Contrast2012" to 10, "Highlights2012" to -45,
            "Shadows2012" to 30, "Whites2012" to -20, "Blacks2012" to -5, "Vibrance" to 10,
            "SaturationAdjustmentOrange" to -10, "LuminanceAdjustmentOrange" to 8, "Texture" to -8,
        ),
        preset(
            "09 Flash + Ambient",
            "IncrementalTemperature" to 12, "Exposure2012" to 0.15, "Contrast2012" to 5, "Highlights2012" to -40,
            "Shadows2012" to 40, "Blacks2012" to 8, "Vibrance" to 12,
            "ColorGradeShadowHue" to 35, "ColorGradeShadowSat" to 12,
            "PostCropVignetteAmount" to -12,
            curve = fadedS,
        ),
        preset(
            "10 Night Party",
            "Exposure2012" to 0.2, "Contrast2012" to 18, "Highlights2012" to -35, "Shadows2012" to 20,
            "Blacks2012" to -12, "Vibrance" to 20, "Saturation" to 10, "Clarity2012" to 8,
            "SaturationAdjustmentPurple" to 15, "SaturationAdjustmentBlue" to 10, "LuminanceAdjustmentOrange" to 10,
            "PostCropVignetteAmount" to -18, "LuminanceSmoothing" to 20,
        ),
        preset(
            "11 Clean B&W",
            "Exposure2012" to 0.05, "Contrast2012" to 25, "Highlights2012" to -30, "Shadows2012" to 20,
            "Whites2012" to 10, "Blacks2012" to -15, "Clarity2012" to 12,
            "GrayMixerOrange" to 15, "GrayMixerBlue" to -20,
            "GrainAmount" to 10,
            text = mapOf("ConvertToGrayscale" to "True"),
            curve = softS,
        ),
    )
}
