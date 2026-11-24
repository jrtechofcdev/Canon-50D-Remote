package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.data.Settings
import com.example.core.video.VideoController
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * Ajustes → Vídeos da câmera: the videos already on the card (.MOV), listed and downloaded to the
 * gallery (Fotos → Vídeos). The 50D has no microphone, so they have no sound.
 */
@Composable
fun VideoSettings(app: TetherApp, @Suppress("UNUSED_PARAMETER") s: Settings) {
    val scope = rememberCoroutineScope()
    val video = app.video
    val state by app.camera.state.collectAsState()
    val connected = state is ConnectionState.Connected
    val cardVideos by video.cardVideos.collectAsState()
    val download by video.download.collectAsState()
    val busy by video.busy.collectAsState()
    var autoDownload by remember { mutableStateOf(video.autoDownload) }
    val ready = connected && busy == null

    SettingsGroup("Vídeos no cartão", Icons.Outlined.SdCard) {
        InfoText("Os vídeos baixados aparecem em Fotos → Vídeos. Sem som: a 50D não tem microfone.")
        CardButton("Procurar no cartão", ready) { scope.launch { runCatching { video.scanCard() } } }
        busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted) }
        download?.let { d ->
            Text("Baixando ${d.name} · ${(d.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Tone.Text)
            LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Tone.Accent)
            CardButton("Cancelar o download", true) { video.cancelDownload() }
        }
        if (cardVideos.isEmpty()) InfoText("Nenhum vídeo listado ainda. Toque em Procurar.")
        cardVideos.forEach { v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${v.filename} · ${VideoController.sizeLabel(v.size)}", style = MaterialTheme.typography.bodyMedium, color = Tone.Text, modifier = Modifier.weight(1f).padding(top = 12.dp))
                CardButton("Baixar", connected && download == null, Modifier) { video.downloadVideo(v) }
            }
        }
        ToggleRow(Icons.Outlined.Download, "Baixar vídeos novos sozinho", "Quando a câmera avisa que gravou um vídeo novo no cartão", autoDownload) {
            autoDownload = it
            video.autoDownload = it
        }
    }
}

@Composable
private fun CardButton(text: String, enabled: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, color = if (enabled) Tone.Text else Tone.TextMuted)
    }
}
