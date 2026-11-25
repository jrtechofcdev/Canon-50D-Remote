package com.example.ui.preset

import androidx.compose.ui.graphics.toArgb
import com.example.core.preset.PresetSettings

/**
 * The editor's panels for the church page, made from [EditorControls]: the same tabs, labels, ranges and
 * defaults as the app's editor, so the browser can't drift from it.
 */
object EditorSpecJson {

    fun encode(): String = buildString {
        append("""{"tabs":[""")
        tab("light", "Luz", groups = listOf("Tom" to EditorControls.light, "Balanço de branco" to EditorControls.wbRelative, "Presença" to EditorControls.presence))
        append(',')
        tab("color", "Cor", groups = emptyList(), extra = mixer())
        append(',')
        tab("effects", "Efeitos", groups = listOf("Efeitos" to EditorControls.effects, "Vinheta" to EditorControls.vignette, "Granulação" to EditorControls.grain))
        append(',')
        tab("detail", "Detalhe", groups = listOf("Nitidez" to EditorControls.sharpening, "Ruído" to EditorControls.noise))
        append(',')
        tab("curve", "Curva", groups = listOf("Curva paramétrica" to EditorControls.parametric), extra = curves())
        append(',')
        tab("grading", "Color grading", groups = listOf("Geral" to EditorControls.gradingCommon), extra = grading())
        append(',')
        tab("calibration", "Calibração", groups = listOf("Calibração" to EditorControls.calibration))
        append("""],"kelvin":""")
        sliders(EditorControls.kelvin)
        append('}')
    }

    private fun StringBuilder.tab(id: String, label: String, groups: List<Pair<String, List<SliderSpec>>>, extra: String? = null) {
        append("""{"id":${q(id)},"label":${q(label)},"groups":[""")
        groups.forEachIndexed { i, (title, list) ->
            if (i > 0) append(',')
            append("""{"title":${q(title)},"sliders":""")
            sliders(list)
            append('}')
        }
        append(']')
        extra?.let { append(',').append(it) }
        append('}')
    }

    private fun StringBuilder.sliders(list: List<SliderSpec>) {
        append('[')
        list.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append("""{"key":${q(s.key)},"label":${q(s.label)},"min":${s.min},"max":${s.max},"def":${s.default},"dec":${s.decimals},"track":${q(s.track.name.lowercase())}}""")
        }
        append(']')
    }

    /** The colour mixer: eight bands, each with hue, saturation and luminance (or the B&W mix). */
    private fun mixer() = buildString {
        append(""""mixer":[""")
        EditorControls.mixer.forEachIndexed { i, (band, color) ->
            if (i > 0) append(',')
            append("""{"band":${q(band)},"label":${q(EditorControls.mixerLabels.getValue(band))},"color":${q(String.format("#%06X", color.toArgb() and 0xFFFFFF))}}""")
        }
        append(']')
    }

    private fun curves() = buildString {
        append(""""curves":[""")
        listOf("RGB", "Vermelho", "Verde", "Azul").forEachIndexed { i, label ->
            if (i > 0) append(',')
            append("""{"key":${q(PresetSettings.CURVE_KEYS[i])},"label":${q(label)}}""")
        }
        append(']')
    }

    private fun grading() = buildString {
        append(""""zones":[""")
        EditorControls.gradingZones.forEachIndexed { i, (key, label) ->
            if (i > 0) append(',')
            append("""{"key":${q(key)},"label":${q(label)}}""")
        }
        append(']')
    }

    private fun q(s: String) = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
                else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }
}
