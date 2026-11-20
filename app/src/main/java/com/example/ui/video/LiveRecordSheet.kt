package com.example.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.theme.Tone
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.camera.LiveState
import com.example.core.video.CameraFilming
import com.example.core.video.PhoneRecording
import com.example.core.video.SetVariant
import kotlinx.coroutines.delay


/**
 * Video in the live view: record on the phone what the camera sends (method B), or step aside so
 * the camera films with its SET button (method A). Neither has sound: the 50D has no microphone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRecordSheet(app: TetherApp, onDismiss: () -> Unit) {
    val video = app.video
    val phone by video.phone.collectAsState()
    val filming by video.filming.collectAsState()
    val live by app.camera.live.collectAsState()
    val liveRunning = live is LiveState.Running

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.example.ui.theme.Tone.Surface) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Vídeo (experimental)", style = MaterialTheme.typography.titleMedium)
            Text("Sem áudio nos dois modos: a 50D não tem microfone.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.Tone.TextMuted)

            Text("Gravar no celular", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "O celular grava a imagem do live view, do jeito que a câmera manda (resolução do live view). Os controles do app não aparecem no vídeo.",
                style = MaterialTheme.typography.bodySmall,
                color = com.example.ui.theme.Tone.TextMuted,
            )
            when (val p = phone) {
                is PhoneRecording.Recording -> Button(
                    onClick = { video.stopPhoneRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Parar · ${elapsed(p.startedAt)} · ${p.size}") }
                PhoneRecording.Saving -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Salvando o vídeo…") }
                PhoneRecording.Idle -> Button(
                    onClick = { video.startPhoneRecording() },
                    enabled = liveRunning && filming == CameraFilming.Off,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (liveRunning) "Gravar no celular" else "Espere a imagem do live view") }
            }

            Text("Gravar pela câmera (botão SET)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            when (val f = filming) {
                is CameraFilming.Waiting -> {
                    Text("Modo SET ativo (${f.variant.label}). Aperte SET na câmera para começar e de novo para parar.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.Tone.Accent)
                    Button(onClick = { video.exitSetMode(searchCard = true, restartLive = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Terminei: buscar o vídeo")
                    }
                    OutlinedButton(onClick = { video.exitSetMode(searchCard = false, restartLive = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Sair do modo SET")
                    }
                }
                CameraFilming.Off -> SetVariant.entries.forEach { variant ->
                    Text(variant.detail, style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.Tone.TextMuted)
                    OutlinedButton(
                        onClick = { video.enterSetMode(variant) },
                        enabled = phone == PhoneRecording.Idle,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(variant.label) }
                }
            }
            Text(
                "Testes de comandos e o log do vídeo ficam em Ajustes → Vídeo (experimental).",
                style = MaterialTheme.typography.bodySmall,
                color = com.example.ui.theme.Tone.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Red "REC 0:42" over the live picture while the phone records, or "SET" while the camera may be filming. */
@Composable
fun RecordingChip(app: TetherApp, modifier: Modifier = Modifier) {
    val phone by app.video.phone.collectAsState()
    val filming by app.video.filming.collectAsState()
    val text = when {
        phone is PhoneRecording.Recording -> "REC " + elapsed((phone as PhoneRecording.Recording).startedAt)
        phone == PhoneRecording.Saving -> "Salvando…"
        filming is CameraFilming.Waiting -> "Modo SET"
        else -> return
    }
    Row(
        modifier.clip(CircleShape).background(Tone.Scrim).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Tone.Error))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Tone.OnPhoto)
    }
}

/** "1:05" since [startedAt], redrawn every second. */
@Composable
private fun elapsed(startedAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val s = ((now - startedAt) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
