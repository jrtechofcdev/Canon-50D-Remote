package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.data.DownloadMode
import com.example.core.data.Settings
import com.example.ui.components.SectionLabel
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: TetherApp) {
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val state by app.camera.state.collectAsState()
    val log by app.camera.log.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickJpg = rememberFolderPicker(app) { scope.launch { app.settings.setJpgFolder(it) } }
    val pickRaw = rememberFolderPicker(app) { scope.launch { app.settings.setRawFolder(it) } }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier
            // Keeps lines readable on tablets instead of stretching edge to edge.
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))

        SectionLabel("SALVAR NO CELULAR")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DownloadMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = settings.downloadMode == mode,
                    onClick = { scope.launch { app.settings.setDownloadMode(mode) } },
                    shape = SegmentedButtonDefaults.itemShape(i, DownloadMode.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Tone.SurfaceHigh,
                        activeContentColor = Tone.Text,
                        inactiveContainerColor = Tone.Background,
                        inactiveContentColor = Tone.TextMuted,
                    ),
                ) { Text(mode.label) }
            }
        }
        Text(
            "A câmera grava tudo no cartão. Aqui você escolhe o que também vem para o celular.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.TextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))
        FolderRow("Pasta dos JPG", app.store.describeTree(settings.jpgFolder), settings.downloadMode.jpg,
            onPick = { pickJpg.launch(null) }, onReset = settings.jpgFolder?.let { { scope.launch { app.settings.setJpgFolder(null) } } })
        Spacer(Modifier.height(12.dp))
        FolderRow("Pasta dos RAW (CR2)", app.store.describeTree(settings.rawFolder), settings.downloadMode.raw,
            onPick = { pickRaw.launch(null) }, onReset = settings.rawFolder?.let { { scope.launch { app.settings.setRawFolder(null) } } })

        Spacer(Modifier.height(28.dp))
        SectionLabel("SESSÃO / CLIENTE")
        var name by remember { mutableStateOf(settings.sessionName) }
        LaunchedEffect(settings.sessionName) { if (name.isEmpty()) name = settings.sessionName }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                scope.launch { app.settings.setSessionName(it) }
            },
            label = { Text("Nome da sessão") },
            placeholder = { Text("Ex.: Casamento Ana e Leo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ToggleRow(
            "Criar subpasta com o nome da sessão",
            "Ex.: …/JPG/${settings.sessionName.ifBlank { "Casamento" }}/",
            settings.sessionSubfolder,
        ) { scope.launch { app.settings.setSessionSubfolder(it) } }
        ToggleRow(
            "Renomear arquivos com o nome da sessão",
            "Ex.: ${settings.sessionName.ifBlank { "Casamento" }}_0001.JPG em vez de IMG_1234.JPG",
            settings.renameWithSession,
        ) { scope.launch { app.settings.setRenameWithSession(it) } }

        Spacer(Modifier.height(28.dp))
        SectionLabel("CÂMERA")
        ToggleRow(
            "Manter a câmera ligada",
            "Evita o desligamento automático da 50D durante a sessão.",
            settings.keepCameraAwake,
        ) { scope.launch { app.settings.setKeepCameraAwake(it) } }
        if (state is ConnectionState.Connected) {
            OutlinedButton(onClick = app.camera::disconnect, modifier = Modifier.padding(top = 8.dp)) {
                Text("Encerrar conexão")
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("DIAGNÓSTICO", Modifier.weight(1f))
            TextButton(onClick = { copyLog(context, log) }) { Text("Copiar log") }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Tone.Surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (log.isEmpty()) Text("Sem eventos ainda.", color = Tone.TextMuted, style = MaterialTheme.typography.bodySmall)
            log.takeLast(40).forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Tone.TextMuted)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun rememberFolderPicker(app: TetherApp, onPicked: (String) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            app.store.takePersistablePermission(uri)
            onPicked(uri.toString())
        }
    }

@Composable
private fun FolderRow(title: String, path: String, active: Boolean, onPick: () -> Unit, onReset: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (active) Tone.Text else Tone.TextMuted)
        Text(path, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        if (!active) Text("Não usada no formato atual", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick) { Text("Escolher pasta") }
            onReset?.let { TextButton(onClick = it) { Text("Usar padrão") } }
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Tone.Accent, checkedThumbColor = Tone.OnAccent),
        )
    }
}

private fun copyLog(context: Context, lines: List<String>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Log Tether 50D", lines.joinToString("\n")))
}
