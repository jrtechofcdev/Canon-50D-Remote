package com.example.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.core.tools.FieldMath
import com.example.core.tools.Flash
import com.example.core.tools.Lens
import com.example.ui.photos.SheetChip
import com.example.ui.theme.ReadoutStyle
import com.example.ui.theme.Tone
import kotlin.math.roundToInt

/** Field calculators: flash exposure for the Yongnuo and depth of field for the 50D's lenses. Offline. */
@Composable
fun ToolsScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                Text("Flash e foco", style = MaterialTheme.typography.titleLarge)
            }
            FlashCalculator()
            DepthCalculator()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FlashCalculator() {
    var flash by rememberSaveable { mutableStateOf(Flash.YN560IV) }
    var power by rememberSaveable { mutableIntStateOf(1) }
    var iso by rememberSaveable { mutableIntStateOf(100) }
    var distance by rememberSaveable { mutableFloatStateOf(3f) }
    val gn = flash.guideNumber

    Card("Flash direto") {
        ChipRow(Flash.entries.map { it.label.removePrefix("Yongnuo ") }, Flash.entries.indexOf(flash)) { flash = Flash.entries[it] }
        Label("Potência")
        ChipRow(FieldMath.POWERS.map { "1/$it" }, FieldMath.POWERS.indexOf(power)) { power = FieldMath.POWERS[it] }
        Label("ISO")
        ChipRow(FieldMath.ISOS.map { it.toString() }, FieldMath.ISOS.indexOf(iso)) { iso = FieldMath.ISOS[it] }
        Label("Distância até a pessoa: ${FieldMath.formatDistance(distance * 1000.0)}")
        ToolSlider(distance, 0.5f..20f) { distance = (it * 10).roundToInt() / 10f }

        val exact = FieldMath.apertureFor(gn, iso, power, distance.toDouble())
        Result(
            "Use " + FieldMath.formatAperture(FieldMath.nearestStop(exact)),
            if (exact < 1.8) "Mesmo em f/1,8 fica escuro: suba a potência ou o ISO." else "Cálculo para o flash direto, cabeça em 105 mm, sem difusor.",
        )
        Label("Alcance máximo em cada abertura")
        FieldMath.APERTURES.forEach { f ->
            Row(Modifier.fillMaxWidth()) {
                Text(FieldMath.formatAperture(f), color = Tone.TextMuted, modifier = Modifier.weight(1f))
                Text(FieldMath.formatDistance(FieldMath.reachFor(gn, iso, power, f) * 1000), style = ReadoutStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize))
            }
        }
        Text(
            "Rebatido no teto ou com softbox, conte 1 a 2 pontos a menos de alcance.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.TextMuted,
        )
    }
}

@Composable
private fun DepthCalculator() {
    var lens by rememberSaveable { mutableStateOf(Lens.Fifty) }
    var focal by rememberSaveable { mutableFloatStateOf(50f) }
    var picked by rememberSaveable { mutableDoubleStateOf(1.8) }
    var distance by rememberSaveable { mutableFloatStateOf(3f) }
    val apertures = if (lens == Lens.Kit) listOf(3.5, 4.0, 5.6, 8.0, 11.0, 16.0) else FieldMath.APERTURES
    val aperture = picked.takeIf { it in apertures } ?: apertures.first()
    val f = if (lens == Lens.Fifty) 50.0 else focal.toDouble()

    Card("Profundidade de campo (50D, fator 1,6×)") {
        ChipRow(Lens.entries.map { it.label }, Lens.entries.indexOf(lens)) {
            lens = Lens.entries[it]
            focal = lens.minFocal.toFloat()
        }
        if (lens == Lens.Kit) {
            Label("Distância focal: ${focal.roundToInt()} mm (equivale a ${(focal * 1.6f).roundToInt()} mm)")
            ToolSlider(focal, 18f..55f) { focal = it.roundToInt().toFloat() }
        }
        Label("Abertura")
        ChipRow(apertures.map(FieldMath::formatAperture), apertures.indexOf(aperture)) { picked = apertures[it] }
        Label("Distância de foco: ${FieldMath.formatDistance(distance * 1000.0)}")
        ToolSlider(distance, 0.3f..20f) { distance = (it * 10).roundToInt() / 10f }

        val dof = FieldMath.depthOfField(f, aperture, distance * 1000.0)
        Result(
            if (dof.infinite) "De ${FieldMath.formatDistance(dof.nearMm)} ao infinito" else "Nítido de ${FieldMath.formatDistance(dof.nearMm)} a ${FieldMath.formatDistance(dof.farMm)}",
            if (dof.infinite) "Tudo a partir de ${FieldMath.formatDistance(dof.nearMm)} fica nítido." else "Faixa nítida: ${FieldMath.formatDistance(dof.totalMm)}.",
        )
        Row(Modifier.fillMaxWidth()) {
            Text("Hiperfocal", color = Tone.TextMuted, modifier = Modifier.weight(1f))
            Text(FieldMath.formatDistance(dof.hyperfocalMm), style = ReadoutStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize))
        }
        Text(
            "Focando na hiperfocal, fica nítido da metade dela até o infinito. Bom para paisagem e grupos grandes.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.TextMuted,
        )
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Tone.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> SheetChip(l, i == selected) { onPick(i) } }
    }
}

@Composable
private fun ToolSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Slider(
        value = value.coerceIn(range),
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(thumbColor = Tone.Text, activeTrackColor = Tone.Accent, inactiveTrackColor = Tone.SurfaceHigh),
    )
}

@Composable
private fun Result(headline: String, detail: String) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Tone.SurfaceHigh).padding(12.dp)) {
        Text(headline, style = MaterialTheme.typography.titleLarge, color = Tone.Accent)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
    }
}
