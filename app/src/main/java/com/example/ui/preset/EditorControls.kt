package com.example.ui.preset

import androidx.compose.ui.graphics.Color
import com.example.core.preset.PresetSettings

/** How a slider's track is painted: plain, or with the colour scale it controls (like Lightroom's WB and hue). */
enum class Track { Plain, Temperature, Tint, Hue }

data class SliderSpec(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float = 0f,
    val decimals: Int = 0,
    val track: Track = Track.Plain,
)

enum class EditorTab(val label: String) {
    Light("Luz"), Color("Cor"), Effects("Efeitos"), Detail("Detalhe"), Curve("Curva"), Grading("Color grading"), Calibration("Calibração")
}

/** Lightroom's panels, with its Portuguese labels, ranges and defaults. */
object EditorControls {
    val light = listOf(
        SliderSpec("Exposure2012", "Exposição", -5f, 5f, decimals = 2),
        SliderSpec("Contrast2012", "Contraste", -100f, 100f),
        SliderSpec("Highlights2012", "Realces", -100f, 100f),
        SliderSpec("Shadows2012", "Sombras", -100f, 100f),
        SliderSpec("Whites2012", "Brancos", -100f, 100f),
        SliderSpec("Blacks2012", "Pretos", -100f, 100f),
    )

    /** JPEG white balance (relative). A raw preset with a Kelvin value gets [kelvin] instead. */
    val wbRelative = listOf(
        SliderSpec("IncrementalTemperature", "Temperatura", -100f, 100f, track = Track.Temperature),
        SliderSpec("IncrementalTint", "Tonalidade", -100f, 100f, track = Track.Tint),
    )
    val kelvin = listOf(
        SliderSpec("Temperature", "Temperatura (K)", 2000f, 12000f, default = 5500f, track = Track.Temperature),
        SliderSpec("Tint", "Tonalidade", -150f, 150f, track = Track.Tint),
    )
    val presence = listOf(
        SliderSpec("Vibrance", "Vibração", -100f, 100f),
        SliderSpec("Saturation", "Saturação", -100f, 100f),
    )

    val effects = listOf(
        SliderSpec("Texture", "Textura", -100f, 100f),
        SliderSpec("Clarity2012", "Claridade", -100f, 100f),
        SliderSpec("Dehaze", "Desembaçar", -100f, 100f),
    )
    val vignette = listOf(
        SliderSpec("PostCropVignetteAmount", "Vinheta", -100f, 100f),
        SliderSpec("PostCropVignetteMidpoint", "Ponto médio", 0f, 100f, default = 50f),
        SliderSpec("PostCropVignetteFeather", "Difusão", 0f, 100f, default = 50f),
        SliderSpec("PostCropVignetteRoundness", "Arredondamento", -100f, 100f),
    )
    val grain = listOf(
        SliderSpec("GrainAmount", "Granulação", 0f, 100f),
        SliderSpec("GrainSize", "Tamanho", 0f, 100f, default = 25f),
        SliderSpec("GrainFrequency", "Aspereza", 0f, 100f, default = 50f),
    )

    val sharpening = listOf(
        SliderSpec("Sharpness", "Nitidez", 0f, 150f),
        SliderSpec("SharpenRadius", "Raio", 0.5f, 3f, default = 1f, decimals = 1),
        SliderSpec("SharpenDetail", "Detalhe", 0f, 100f, default = 25f),
        SliderSpec("SharpenEdgeMasking", "Máscara", 0f, 100f),
    )
    val noise = listOf(
        SliderSpec("LuminanceSmoothing", "Redução de ruído", 0f, 100f),
        SliderSpec("ColorNoiseReduction", "Ruído de cor", 0f, 100f),
    )

    val parametric = listOf(
        SliderSpec("ParametricHighlights", "Realces", -100f, 100f),
        SliderSpec("ParametricLights", "Claros", -100f, 100f),
        SliderSpec("ParametricDarks", "Escuros", -100f, 100f),
        SliderSpec("ParametricShadows", "Sombras", -100f, 100f),
    )

    val calibration = listOf(
        SliderSpec("ShadowTint", "Sombras (tonalidade)", -100f, 100f, track = Track.Tint),
        SliderSpec("RedHue", "Vermelho primário · matiz", -100f, 100f),
        SliderSpec("RedSaturation", "Vermelho primário · saturação", -100f, 100f),
        SliderSpec("GreenHue", "Verde primário · matiz", -100f, 100f),
        SliderSpec("GreenSaturation", "Verde primário · saturação", -100f, 100f),
        SliderSpec("BlueHue", "Azul primário · matiz", -100f, 100f),
        SliderSpec("BlueSaturation", "Azul primário · saturação", -100f, 100f),
    )

    val gradingZones = listOf("Shadow" to "Sombras", "Midtone" to "Meios-tons", "Highlight" to "Realces", "Global" to "Global")
    val gradingCommon = listOf(
        SliderSpec("ColorGradeBlending", "Mistura", 0f, 100f, default = 50f),
        SliderSpec("ColorGradeBalance", "Equilíbrio", -100f, 100f),
    )

    /** The eight Colour Mixer bands, with the swatch colour shown for each. */
    val mixer = listOf(
        "Red" to Color(0xFFE5484D), "Orange" to Color(0xFFF08A3C), "Yellow" to Color(0xFFEFD34A), "Green" to Color(0xFF46A758),
        "Aqua" to Color(0xFF3CC4C9), "Blue" to Color(0xFF3E63DD), "Purple" to Color(0xFF8E4EC6), "Magenta" to Color(0xFFD6409F),
    )
    val mixerLabels = mapOf(
        "Red" to "Vermelho", "Orange" to "Laranja", "Yellow" to "Amarelo", "Green" to "Verde",
        "Aqua" to "Ciano", "Blue" to "Azul", "Purple" to "Roxo", "Magenta" to "Magenta",
    )

    fun usesKelvin(s: PresetSettings) = s.values["WhiteBalance"] == "Custom" && s.values.containsKey("Temperature")

    fun isMono(s: PresetSettings) = s.bool("ConvertToGrayscale") || s.values["Treatment"] == "Black & White"
}
