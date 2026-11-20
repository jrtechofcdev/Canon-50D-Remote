package com.example.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.data.Settings
import com.example.core.log.AppLog
import com.example.core.ptp.EosProp
import com.example.core.ptp.PtpOp
import com.example.core.video.CameraFilming
import com.example.core.video.SetVariant
import com.example.core.video.VideoController
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ajustes → Vídeo (experimental). Tests what this 50D does with video over the cable and keeps
 * the module's own log in sight. Every button writes its result as VIDEO-… lines.
 */
@Composable
fun VideoSettings(app: TetherApp, s: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val video = app.video
    val state by app.camera.state.collectAsState()
    val connected = state is ConnectionState.Connected
    val filming by video.filming.collectAsState()
    val cardVideos by video.cardVideos.collectAsState()
    val download by video.download.collectAsState()
    val monitoring by video.monitoring.collectAsState()
    val busy by video.busy.collectAsState()
    val recent by AppLog.recent.collectAsState()
    var autoDownload by remember { mutableStateOf(video.autoDownload) }
    var bitrate by remember { mutableIntStateOf(video.bitRateKbps) }
    val ready = connected && busy == null

    SettingsGroup("Sobre este módulo", Icons.Outlined.Info) {
        InfoText(
            "A 50D saiu de fábrica sem vídeo. Se a sua filma com o botão SET e acende o LED vermelho, ela tem um firmware " +
                "extra (o comportamento é o do Magic Lantern). A câmera não tem microfone: os vídeos não têm som. " +
                "Tudo aqui é experimental e grava o que acontece no log, nas linhas VIDEO.",
        )
    }

    SettingsGroup("Gravar pela câmera (botão SET)", Icons.Outlined.Videocam) {
        InfoText("O app sai do caminho para o SET da câmera funcionar e depois busca o arquivo no cartão. Sem áudio.")
        when (val f = filming) {
            is CameraFilming.Waiting -> {
                Text("Modo SET ativo: ${f.variant.label}. Filme na câmera e toque em Terminei.", style = MaterialTheme.typography.bodyMedium, color = Tone.Accent)
                LabButton("Terminei: buscar o vídeo no cartão", connected) { video.exitSetMode(searchCard = true, restartLive = false) }
                LabButton("Sair sem buscar", true) { video.exitSetMode(searchCard = false, restartLive = false) }
            }
            CameraFilming.Off -> SetVariant.entries.forEach { variant ->
                Text(variant.detail, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.padding(top = 6.dp))
                LabButton(variant.label, ready) { video.enterSetMode(variant) }
            }
        }
    }

    SettingsGroup("Vídeos no cartão", Icons.Outlined.SdCard) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("Procurar no cartão", ready, Modifier.weight(1f)) { scope.launch { runCatching { video.scanCard() } } }
            LabButton("Reconectar e procurar", connected && busy == null, Modifier.weight(1f)) { video.reconnectAndScan() }
        }
        busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted) }
        download?.let { d ->
            Text("Baixando ${d.name} · ${(d.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Tone.Text)
            LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Tone.Accent)
            LabButton("Cancelar o download", true) { video.cancelDownload() }
        }
        if (cardVideos.isEmpty()) InfoText("Nenhum vídeo listado ainda. Toque em Procurar.")
        cardVideos.forEach { v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${v.filename} · ${VideoController.sizeLabel(v.size)}", style = MaterialTheme.typography.bodyMedium, color = Tone.Text, modifier = Modifier.weight(1f).padding(top = 12.dp))
                LabButton("Baixar", connected && download == null) { video.downloadVideo(v) }
            }
        }
        ToggleRow(Icons.Outlined.Download, "Baixar vídeos novos sozinho", "Quando a câmera anuncia um vídeo ou ele aparece depois do modo SET", autoDownload) {
            autoDownload = it
            video.autoDownload = it
        }
    }

    SettingsGroup("Gravação no celular", Icons.Outlined.Tune) {
        InfoText("No live view, o botão de vídeo grava no celular a imagem que a câmera manda, na resolução do live view (bem menor que a foto). Sem áudio.")
        Text("Qualidade: $bitrate kbps", style = MaterialTheme.typography.bodyMedium, color = Tone.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4_000 to "Leve", 8_000 to "Normal", 16_000 to "Alta").forEach { (kbps, label) ->
                LabButton(if (bitrate == kbps) "• $label" else label, true, Modifier.weight(1f)) {
                    bitrate = kbps
                    video.bitRateKbps = kbps
                }
            }
        }
    }

    SettingsGroup("Comandos de teste", Icons.Outlined.Science) {
        InfoText(
            "Mandam para a 50D comandos de vídeo de câmeras Canon mais novas e gravam a resposta. " +
                "O esperado é que a câmera recuse a maioria; a resposta é o que importa para o diagnóstico.",
        )
        LabButton("Gravar no log o que a câmera anuncia", connected) { video.dumpCapabilities() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("Filmar (0xD1B8 = 4)", ready, Modifier.weight(1f)) {
                video.labCommand("Filmar pela USB (0xD1B8 = 4)") { it.rawSetProperty(EosProp.EVF_RECORD_STATUS, EosProp.EVF_RECORD_START) }
            }
            LabButton("Parar (0xD1B8 = 0)", ready, Modifier.weight(1f)) {
                video.labCommand("Parar filme pela USB (0xD1B8 = 0)") { it.rawSetProperty(EosProp.EVF_RECORD_STATUS, EosProp.EVF_RECORD_STOP) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("Chave filme ON", ready, Modifier.weight(1f)) { video.labCommand("Chave de filme ON (0x9133)") { it.rawCommand(PtpOp.EOS_MOVIE_SELECT_SW_ON) } }
            LabButton("Chave filme OFF", ready, Modifier.weight(1f)) { video.labCommand("Chave de filme OFF (0x9134)") { it.rawCommand(PtpOp.EOS_MOVIE_SELECT_SW_OFF) } }
        }
        Text("Saída do live view (0xD1B0)", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Desligado", 1 to "Câmera", 2 to "Celular", 3 to "Os dois").forEach { (v, label) ->
                LabButton(label, ready, Modifier.weight(1f)) { video.labCommand("Saída do live view = $v ($label)") { it.rawSetProperty(EosProp.EVF_OUTPUT_DEVICE, v) } }
            }
        }
        ToggleRow(Icons.Outlined.Sensors, "Monitor de eventos da câmera", "Grava no log tudo o que a câmera avisa, inclusive enquanto filma", monitoring) { video.setMonitor(it) }
    }

    SettingsGroup("Log do vídeo", Icons.Outlined.Description) {
        InfoText("Só as linhas do módulo de vídeo. O arquivo de log do app tem tudo junto, com estas linhas marcadas VIDEO e uma parte só delas no fim do envio.")
        val lines = recent.filter { AppLog.isVideoLine(it) }.takeLast(40)
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (lines.isEmpty()) Text("Nada ainda.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Tone.TextMuted) }
        }
        LabButton("Enviar o log", true) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { AppLog.bundle() }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Enviar o log"))
            }
        }
    }
}

@Composable
private fun LabButton(text: String, enabled: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, color = if (enabled) Tone.Text else Tone.TextMuted)
    }
}
