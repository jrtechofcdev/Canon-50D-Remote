package com.example.ui.preset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.preset.Preset
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/** Picks the preset the session lays over every new frame (the "look"), or none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLookSheet(app: TetherApp, presets: List<Preset>, settings: Settings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sample by produceState<String?>(null) {
        value = app.database.photoDao().latestDisplayUri() ?: SampleImage.uri(context)
    }

    fun pick(id: Long) = scope.launch {
        app.settings.set(SettingKeys.sessionPresetId, id)
        app.settings.set(SettingKeys.sessionAdjust, "")
        if (id > 0) app.settings.set(SettingKeys.applyPresetToNew, true)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Look da sessão", style = MaterialTheme.typography.titleMedium)
            Text(
                "Aplicado em cada foto nova, como um perfil de cor. O arquivo original não muda.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aplicar nas fotos novas", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.applyPresetToNew,
                    onCheckedChange = { on -> scope.launch { app.settings.set(SettingKeys.applyPresetToNew, on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Tone.Accent, checkedThumbColor = Tone.OnAccent),
                )
            }
            if (settings.sessionAdjust.isNotBlank()) {
                Text(
                    "Com a calibração levada de uma foto. Escolher um preset aqui descarta essa calibração.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.Info,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    LookRow("Nenhum", selected = settings.sessionPresetId <= 0, thumb = null) { pick(-1L) }
                }
                items(presets, key = { it.id }) { p ->
                    LookRow(p.name, selected = p.id == settings.sessionPresetId, thumb = { PresetThumb(app, p, sample, Modifier.size(48.dp)) }) {
                        pick(p.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun LookRow(name: String, selected: Boolean, thumb: (@Composable () -> Unit)?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).heightIn(min = 56.dp).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        thumb?.invoke()
        Text(name, modifier = Modifier.weight(1f), color = if (selected) Tone.Accent else Tone.Text)
        if (selected) Text("Em uso", style = MaterialTheme.typography.labelMedium, color = Tone.Accent)
    }
}
