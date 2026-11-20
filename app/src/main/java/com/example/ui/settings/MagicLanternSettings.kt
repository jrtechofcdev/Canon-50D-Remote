package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.data.Settings
import com.example.core.ml.CameraFirmware
import com.example.core.ml.MlState
import com.example.core.ptp.EosProp
import com.example.ui.theme.Tone

/**
 * Ajustes → Magic Lantern. The camera runs Canon's firmware some days and Magic Lantern on others:
 * here the app is told which (or asks the camera) and offers what only ML does. Lines go to the log as ML.
 */
@Composable
fun MagicLanternSettings(app: TetherApp, s: Settings) {
    val ml = app.magicLantern
    val state by ml.state.collectAsState()
    val firmware by ml.firmware.collectAsState()
    val active by ml.active.collectAsState()
    val menus by ml.menus.collectAsState()
    val connection by app.camera.state.collectAsState()
    val connected = connection is ConnectionState.Connected

    SettingsGroup("Firmware da câmera", Icons.Outlined.Memory) {
        ChoiceRow("Firmware", CameraFirmware.entries.map { it to it.label }, firmware) { ml.setFirmware(it) }
        InfoText(
            when (firmware) {
                CameraFirmware.AUTO -> "A cada conexão o app pergunta à câmera se o Magic Lantern está rodando e se adapta sozinho."
                CameraFirmware.CANON -> "O app não pergunta nada à câmera e usa só o que o firmware da Canon faz (sem filmar pela câmera)."
                CameraFirmware.ML -> "O app oferece as funções do Magic Lantern mesmo que a câmera não responda à consulta."
            },
        )
        val status = when (val st = state) {
            MlState.NoCamera -> "Conecte a câmera."
            MlState.Checking -> "Perguntando à câmera…"
            MlState.Canon -> "A câmera respondeu como firmware Canon original (o Magic Lantern não respondeu)."
            MlState.NotAsked -> "Firmware Canon original (escolhido aqui)."
            is MlState.Loaded -> "Magic Lantern rodando · ${st.build.describe()}"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = if (state is MlState.Loaded) Tone.Accent else Tone.Text, modifier = Modifier.padding(vertical = 4.dp))
        MlButton("Perguntar à câmera agora", connected && firmware != CameraFirmware.CANON) { ml.detect() }
    }

    SettingsGroup("O que muda com o Magic Lantern", Icons.Outlined.AutoAwesome) {
        InfoText(
            "Disparo, live view, download e entrega funcionam igual nos dois firmwares. Com o Magic Lantern " +
                "ativo, o app também mostra \"Gravar pela câmera (botão SET)\" no vídeo do live view, e a Sessão mostra " +
                "\"Magic Lantern\" ao lado do nome da câmera. Os vídeos ficam sem som: a 50D não tem microfone.",
        )
    }

    SettingsGroup("Filmar pela câmera", Icons.Outlined.Videocam) {
        InfoText(
            "O Magic Lantern só filma com o live view ligado na câmera, e não existe comando pelo cabo para começar a filmar: " +
                "o SET da câmera é que começa. No live view do app, toque no ícone de vídeo e use \"Câmera livre\".",
        )
        InfoText("Teste: com o live view da câmera ligado, estes botões pedem o filme pelo comando da Canon (0xD1B8). O resultado vai para o log.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MlButton("Filmar (0xD1B8 = 4)", connected && active, Modifier.weight(1f)) {
                app.video.labCommand("Filmar pelo app (0xD1B8 = 4)") { it.rawSetProperty(EosProp.EVF_RECORD_STATUS, EosProp.EVF_RECORD_START) }
            }
            MlButton("Parar (0xD1B8 = 0)", connected && active, Modifier.weight(1f)) {
                app.video.labCommand("Parar pelo app (0xD1B8 = 0)") { it.rawSetProperty(EosProp.EVF_RECORD_STATUS, EosProp.EVF_RECORD_STOP) }
            }
        }
    }

    SettingsGroup("Menus do Magic Lantern", Icons.Outlined.Menu) {
        InfoText("Lê pelo cabo os menus que este Magic Lantern tem, para saber quais funções ele traz. Só leitura: mudar menus pelo cabo não é seguro no código atual do Magic Lantern.")
        MlButton("Ler os menus", connected && state is MlState.Loaded) { ml.readMenus() }
        if (menus.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                menus.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.Text) }
            }
        }
    }

    SettingsGroup("Usar sem o Magic Lantern", Icons.Outlined.Info) {
        InfoText(
            "O jeito mais simples de alternar: um cartão com o Magic Lantern e outro sem. Ligando com o cartão sem o ML, " +
                "a 50D roda o firmware da Canon. No modo Automático o app percebe a troca na conexão seguinte.",
        )
    }
}

@Composable
private fun MlButton(text: String, enabled: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
