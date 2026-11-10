package com.example.ui.live

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.preset.Preset
import com.example.core.preset.forPicking
import com.example.ui.photos.SheetChip
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/** Everything drawn over the live picture, and the look applied to it. Choices are remembered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveOptionsSheet(app: TetherApp, s: Settings, presets: List<Preset>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    fun <T> set(key: Preferences.Key<T>, value: T) = scope.launch { app.settings.set(key, value) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Monitor", style = MaterialTheme.typography.titleMedium)

            Label("Preset em tempo real")
            ChipLine {
                SheetChip("Sem preset", s.livePresetId < 0) { set(SettingKeys.livePresetId, -1L) }
                SheetChip("Look da sessão", s.livePresetId == 0L) { set(SettingKeys.livePresetId, 0L) }
                presets.forPicking().forEach { p -> SheetChip(if (p.favorite) "★ ${p.name}" else p.name, s.livePresetId == p.id) { set(SettingKeys.livePresetId, p.id) } }
            }
            Text(
                "Mostra a imagem como ela vai ficar, igual a um perfil de cor. As fotos continuam sendo gravadas normalmente.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )

            Label("Guia de enquadramento")
            ChipLine { LiveGrid.entries.forEach { g -> SheetChip(g.label, s.liveGrid == g.id) { set(SettingKeys.liveGrid, g.id) } } }

            Toggle(Icons.Outlined.WbSunny, "Zebra (áreas estouradas)", s.liveZebra) { set(SettingKeys.liveZebra, it) }
            if (s.liveZebra) ChipLine {
                listOf(235 to "92%", 245 to "96%", 250 to "98%", 254 to "100%").forEach { (v, l) ->
                    SheetChip(l, s.liveZebraLevel == v) { set(SettingKeys.liveZebraLevel, v) }
                }
            }
            Toggle(Icons.Outlined.CenterFocusStrong, "Focus peaking (o que está em foco)", s.livePeaking) { set(SettingKeys.livePeaking, it) }
            if (s.livePeaking) {
                ChipLine {
                    listOf("mint" to "Menta", "red" to "Vermelho", "cyan" to "Ciano", "white" to "Branco").forEach { (id, l) ->
                        SheetChip(l, s.livePeakingColor == id) { set(SettingKeys.livePeakingColor, id) }
                    }
                }
                ChipLine {
                    listOf(1 to "Pouco", 2 to "Médio", 3 to "Muito").forEach { (v, l) ->
                        SheetChip("Sensibilidade: $l", s.livePeakingSensitivity == v) { set(SettingKeys.livePeakingSensitivity, v) }
                    }
                }
            }
            Toggle(Icons.Outlined.BarChart, "Histograma", s.liveHistogram) { set(SettingKeys.liveHistogram, it) }
            Toggle(Icons.Outlined.TouchApp, "Toque escolhe o ponto de foco", s.liveTapToFocus) { set(SettingKeys.liveTapToFocus, it) }
            if (s.liveTapToFocus) Toggle(Icons.Outlined.CenterFocusWeak, "Toque também foca (AF)", s.liveTapAf) { set(SettingKeys.liveTapAf, it) }

            Label("Silêncio e foco")
            Toggle(Icons.Outlined.VolumeOff, "AF silencioso (Modo Live)", s.liveSilentAf) { set(SettingKeys.liveSilentAf, it) }
            Text(
                "Desligado (padrão): Modo rápido, o foco mais certeiro da 50D; o espelho desce a cada foco e faz barulho. " +
                    "Ligado: Modo Live, foco pelo sensor sem o espelho, mais silencioso e mais lento (até 3 s, e pode falhar com pouca luz). " +
                    "Na câmera, deixe Disparo silencioso em Modo 1 (Modo 2 e Desativado atrapalham o disparo pelo cabo).",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
            Toggle(Icons.Outlined.CenterFocusStrong, "Focar antes de disparar", s.shutterFocusFirst) { set(SettingKeys.shutterFocusFirst, it) }
            Label("Esperar o foco antes de desistir")
            ChipLine {
                listOf(3, 5, 8, 12).forEach { v -> SheetChip("$v s", s.shutterWait == v) { set(SettingKeys.shutterWait, v) } }
            }
            Label("Fluidez (quadros por segundo)")
            ChipLine {
                listOf(10 to "10 · economiza", 15 to "15", 20 to "20", 30 to "30 · máximo").forEach { (v, l) ->
                    SheetChip(l, s.liveFps == v) { set(SettingKeys.liveFps, v) }
                }
            }
            Toggle(Icons.Outlined.Flip, "Espelhar a imagem", s.liveMirror) { set(SettingKeys.liveMirror, it) }
            Toggle(Icons.Outlined.CameraAlt, "Mostrar também na tela da câmera", s.liveOnCamera) { set(SettingKeys.liveOnCamera, it) }
            Text(
                "Live view gasta mais bateria da 50D. Com a imagem só no celular, a tela da câmera fica apagada.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun ChipLine(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (checked) Tone.Accent else Tone.TextMuted, modifier = Modifier.padding(end = 12.dp).size(22.dp))
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Tone.Accent, checkedThumbColor = Tone.OnAccent),
        )
    }
}
