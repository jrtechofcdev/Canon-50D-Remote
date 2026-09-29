package com.example.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.log.AppLog
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ajustes → Desenvolvedor, unlocked by tapping the brand at the bottom of Ajustes seven times.
 * Tools to see what the camera answers, try the whole saving path without the 50D and collect what is
 * needed when something goes wrong.
 */
@Composable
fun DeveloperSettings(app: TetherApp, s: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by app.camera.status.collectAsState()
    val state by app.camera.state.collectAsState()
    val recent by AppLog.recent.collectAsState()
    var crashAsk by remember { mutableStateOf(false) }
    val connected = state is ConnectionState.Connected
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_LONG).show()

    SettingsGroup("Câmera", Icons.Outlined.CameraAlt) {
        InfoText(if (connected) "Conectada. Os testes abaixo mandam comandos de verdade para a 50D." else "Conecte a câmera para usar os testes de câmera.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            DevButton("Testar foco", connected, Modifier.weight(1f)) { app.camera.liveAutofocus(); toast("Foco pedido. Veja as linhas FOCO abaixo.") }
            DevButton("Testar disparo", connected, Modifier.weight(1f)) { app.camera.capture(s.shutterWait, focusFirst = false) }
        }
        DevButton("Reconectar a USB", true, Modifier.fillMaxWidth()) {
            app.camera.disconnect()
            scope.launch { kotlinx.coroutines.delay(800); app.camera.findAndConnect() }
        }
        Text("Propriedades lidas (${status.values.size})", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted, modifier = Modifier.padding(top = 10.dp))
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (status.values.isEmpty()) Text("Nenhuma ainda: aparecem quando a câmera manda os eventos.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
            status.values.entries.sortedBy { it.key }.forEach { (prop, value) ->
                Text("0x%04X = 0x%X (%d)".format(prop, value, value), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Tone.Text)
            }
        }
    }

    SettingsGroup("Log detalhado", Icons.Outlined.Code) {
        ToggleRow(Icons.Outlined.Usb, "Cada comando da câmera no log", "Grava o código e o tempo de cada operação USB (deixa o log bem maior)", s.devVerboseLog) {
            scope.launch { app.settings.set(SettingKeys.devVerboseLog, it) }
        }
        val lines = recent.filter { it.contains("FOCO") || it.contains("DISPARO") || it.contains("PTP") || it.contains("DEV") }.takeLast(14)
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Tone.TextMuted) }
        }
    }

    SettingsGroup("Testes sem a câmera", Icons.Outlined.Photo) {
        InfoText("Salva uma foto de teste pelo mesmo caminho das fotos da câmera: pastas, 2º cartão, look da sessão, galeria e modo cliente.")
        DevButton("Simular foto recebida", true, Modifier.fillMaxWidth()) {
            scope.launch {
                runCatching { app.tether.simulatePhoto() }
                    .onSuccess { toast("${it.savedBase} salva") }
                    .onFailure { toast("Falhou: ${it.message}") }
            }
        }
    }

    SettingsGroup("Processamento", Icons.Outlined.Memory) {
        ToggleRow(Icons.Outlined.Memory, "Processar presets só pela CPU", "Para comparar com a GPU quando alguma cor parecer errada (mais lento)", s.devRender == "cpu") {
            scope.launch { app.settings.set(SettingKeys.devRender, if (it) "cpu" else "auto"); app.looks.trim() }
        }
        Text(AppLog.memory(), style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
    }

    SettingsGroup("Dados", Icons.Outlined.Storage) {
        DevButton("Exportar o banco de dados", true, Modifier.fillMaxWidth()) {
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    val db = context.getDatabasePath("tether50d.db")
                    val dir = File(context.cacheDir, "export").apply { mkdirs() }
                    File(dir, "JR_C50D_Sync_banco.db").also { db.copyTo(it, overwrite = true) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Enviar o banco"))
            }
        }
        DevButton("Limpar caches (Loja e cópias temporárias)", true, Modifier.fillMaxWidth()) {
            scope.launch {
                val freed = withContext(Dispatchers.IO) {
                    listOf("shop", "export").sumOf { name -> File(context.cacheDir, name).let { d -> d.walkBottomUp().filter { it.isFile }.sumOf { it.length() }.also { d.deleteRecursively() } } }
                }
                app.shop.trim()
                app.looks.trim()
                toast("%.1f MB liberados".format(freed / 1_048_576.0))
            }
        }
    }

    SettingsGroup("Teste de fechamento", Icons.Outlined.BugReport) {
        InfoText("Fecha o app de propósito, para conferir se o log grava o erro e mostra o aviso na próxima abertura.")
        DevButton("Fechar o app com um erro", true, Modifier.fillMaxWidth()) { crashAsk = true }
    }

    SettingsGroup("Modo desenvolvedor", Icons.Outlined.CleaningServices) {
        DevButton("Desligar o modo desenvolvedor", true, Modifier.fillMaxWidth()) {
            scope.launch {
                app.settings.set(SettingKeys.developerMode, false)
                app.settings.set(SettingKeys.devVerboseLog, false)
                app.settings.set(SettingKeys.devRender, "auto")
            }
            toast("Modo desenvolvedor desligado")
        }
    }

    if (crashAsk) {
        AlertDialog(
            onDismissRequest = { crashAsk = false },
            containerColor = Tone.Surface,
            icon = { androidx.compose.material3.Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null) },
            title = { Text("Fechar o app agora?") },
            text = { Text("É só um teste do log. Abra o app de novo e veja o aviso em Diagnóstico.") },
            confirmButton = {
                TextButton(onClick = { throw IllegalStateException("Teste de fechamento pelo modo desenvolvedor") }) { Text("Fechar", color = Tone.Error) }
            },
            dismissButton = { TextButton(onClick = { crashAsk = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DevButton(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) { Text(text, color = if (enabled) Tone.Text else Tone.TextMuted) }
}
