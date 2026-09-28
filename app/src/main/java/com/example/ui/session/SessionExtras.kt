package com.example.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.camera.BracketState
import com.example.core.camera.CameraStatus
import com.example.core.data.ExposurePreset
import androidx.compose.material.icons.outlined.AutoAwesome
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.example.core.messages.MessageKind
import com.example.core.messages.MessageTemplates
import com.example.core.storage.FileKind
import com.example.ui.photos.MessageSheet
import com.example.core.backup.BackupState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Flag
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.ui.PhotoActions
import com.example.ui.ShareSet
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/** Shown when the card has frames shot while the cable was out and "perguntar antes" is on. */
@Composable
fun PendingBanner(count: Int, onDownload: () -> Unit, onDismiss: () -> Unit) {
    if (count == 0) return
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Tone.Surface).padding(12.dp),
    ) {
        Text(
            if (count == 1) "1 foto no cartão ainda não veio para o celular" else "$count fotos no cartão ainda não vieram para o celular",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Baixar agora") }
            TextButton(onClick = onDismiss) { Text("Ignorar") }
        }
    }
}

/** Secondary tools. Each chip appears only when its feature is switched on in Ajustes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsRow(
    settings: Settings,
    connected: Boolean,
    bracket: BracketState?,
    hasSessionPhotos: Boolean,
    onPresets: () -> Unit,
    onBracket: () -> Unit,
    onCard: () -> Unit,
    onClient: () -> Unit,
    onNight: () -> Unit,
    onSummary: () -> Unit,
    sessionLook: String?,
    onLook: () -> Unit,
    onMoment: () -> Unit,
    onTools: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Tool(sessionLook?.let { "Look: $it" } ?: "Look: nenhum", Icons.Outlined.AutoAwesome, true, onLook)
        if (settings.momentsEnabled) {
            Tool(settings.currentMoment.ifBlank { null }?.let { "Momento: $it" } ?: "Momento", Icons.Outlined.Flag, true, onMoment)
        }
        if (settings.presetsEnabled) Tool("Exposições", Icons.Outlined.Tune, connected, onPresets)
        if (settings.aebEnabled) {
            val label = bracket?.let { "AEB ${it.shot}/${it.total}" } ?: "AEB ${settings.aebShots}×"
            Tool(label, Icons.Outlined.Exposure, connected && bracket == null, onBracket)
        }
        if (settings.cardBrowserEnabled) Tool("Cartão", Icons.Outlined.SdCard, connected, onCard)
        if (settings.clientModeEnabled) Tool("Modo cliente", Icons.Outlined.Slideshow, true, onClient)
        Tool(if (settings.nightMode) "Modo normal" else "Modo noite", Icons.Outlined.Bedtime, true, onNight)
        if (settings.toolsEnabled) Tool("Flash e foco", Icons.Outlined.Calculate, true, onTools)
        if (settings.sessionSummary && hasSessionPhotos) Tool("Finalizar sessão", Icons.Outlined.Summarize, true, onSummary)
    }
}

@Composable
private fun Tool(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(labelColor = Tone.Text, leadingIconContentColor = Tone.TextMuted),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsSheet(
    presets: List<ExposurePreset>,
    status: CameraStatus,
    connected: Boolean,
    onApply: (ExposurePreset) -> Unit,
    onSave: (ExposurePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Exposições salvas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (presets.isEmpty()) {
                Text("Nenhuma exposição salva. Ajuste a câmera e salve a combinação atual.", color = Tone.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            presets.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = connected) { onApply(p) }
                        .heightIn(min = 56.dp)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        Text(describe(p), style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                    }
                    Text(if (connected) "Aplicar" else "Conecte a câmera", style = MaterialTheme.typography.labelLarge, color = if (connected) Tone.Accent else Tone.TextMuted)
                }
                HorizontalDivider(color = Tone.Line)
            }
            Spacer(Modifier.height(12.dp))
            if (naming) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("Nome") }, placeholder = { Text("Ex.: Igreja interna") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            onSave(fromStatus(name.trim(), status))
                            naming = false
                            name = ""
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                    ) { Text("Salvar") }
                    TextButton(onClick = { naming = false }) { Text("Cancelar") }
                }
            } else {
                OutlinedButton(onClick = { naming = true }, enabled = connected) { Text("Salvar ajuste atual") }
            }
        }
    }
}

private fun fromStatus(name: String, status: CameraStatus) = ExposurePreset(
    name = name,
    iso = status.value(EosProp.ISO),
    aperture = status.value(EosProp.APERTURE),
    shutter = status.value(EosProp.SHUTTER_SPEED),
    exposureCompensation = status.value(EosProp.EXPOSURE_COMPENSATION),
    whiteBalance = status.value(EosProp.WHITE_BALANCE),
)

private fun describe(p: ExposurePreset) = listOfNotNull(
    p.shutter?.let { EosValues.label(EosProp.SHUTTER_SPEED, it) },
    p.aperture?.let { EosValues.label(EosProp.APERTURE, it) },
    p.iso?.let { "ISO " + EosValues.label(EosProp.ISO, it) },
    p.exposureCompensation?.let { EosValues.label(EosProp.EXPOSURE_COMPENSATION, it) + " EV" },
    p.whiteBalance?.let { EosValues.label(EosProp.WHITE_BALANCE, it) },
).joinToString(" · ")

/** End-of-session recap with one-tap delivery of the picks to Lightroom. */
@Composable
fun SessionSummaryDialog(app: TetherApp, actions: PhotoActions, settings: Settings, onEnd: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val photos by produceState<List<Photo>?>(null, settings.sessionKey) {
        value = app.database.photoDao().inSession(settings.sessionKey)
    }
    val list = photos ?: return
    val favorites = list.filter { it.favorite }
    val rated = list.filter { it.rating >= 3 }
    val bytes = list.sumOf { it.jpgSize + it.rawSize }
    val minutes = if (list.size > 1) (list.last().capturedAt - list.first().capturedAt) / 60_000 else 0
    val picks = list.filter { it.clientPick }
    val context = LocalContext.current
    var message by remember { mutableStateOf<Pair<MessageKind, List<Photo>>?>(null) }
    var copying by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        title = { Text("Sessão ${settings.sessionKey}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryLine("Fotos recebidas", list.size.toString())
                SummaryLine("RAW / JPG", "${list.count { it.rawUri != null }} / ${list.count { it.jpgUri != null }}")
                SummaryLine("Espaço usado", "%.1f GB".format(bytes / 1_073_741_824.0))
                SummaryLine("Duração", if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min")
                SummaryLine("Favoritas", favorites.size.toString())
                if (settings.ratingEnabled) SummaryLine("3 estrelas ou mais", rated.size.toString())
                if (settings.clientPicksEnabled) SummaryLine("Escolhidas pelo cliente", picks.size.toString())
                Spacer(Modifier.height(8.dp))
                if (settings.clientPicksEnabled && picks.isNotEmpty()) {
                    Text("Seleção do cliente", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
                    OutlinedButton(
                        onClick = {
                            val text = MessageTemplates.lightroomFilter(picks.map { it.savedBase })
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Seleção do cliente", text))
                            Toast.makeText(context, "Lista copiada. No Lightroom Classic: Filtro de texto → Contém qualquer.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copiar lista para o Lightroom") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                var done = 0
                                withContext(Dispatchers.IO) {
                                    picks.forEach { p ->
                                        listOfNotNull(p.jpgUri, p.rawUri, p.editedUri).forEach { uri ->
                                            copying = "Copiando ${++done}…"
                                            runCatching { app.store.copy(uri, FileKind.SELECTED, settings) }
                                        }
                                    }
                                }
                                copying = null
                                Toast.makeText(context, "$done arquivo(s) na pasta Escolhidas", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = copying == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(copying ?: "Copiar escolhidas para a pasta") }
                    OutlinedButton(onClick = { message = MessageKind.Selection to picks }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mandar a seleção no WhatsApp")
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedButton(onClick = { message = MessageKind.Preview to list }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mensagem para o cliente")
                }
                BackupPanel(app, settings)
                val sendSet = if (settings.downloadMode.raw) ShareSet.RAW else ShareSet.JPG
                OutlinedButton(
                    onClick = { scope.launch { actions.openInLightroom(favorites, sendSet, settings) } },
                    enabled = favorites.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Outlined.Collections, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Favoritas no Lightroom") }
                if (settings.ratingEnabled) {
                    OutlinedButton(
                        onClick = { scope.launch { actions.openInLightroom(rated, sendSet, settings) } },
                        enabled = rated.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("3★ ou mais no Lightroom") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onEnd,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Encerrar conexão") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Continuar fotografando") } },
    )

    message?.let { (kind, photos) ->
        MessageSheet(actions, settings, photos, initial = kind, onDismiss = { message = null })
    }
}

/** Copy of the session to the backup folder, with the result of the size check. */
@Composable
private fun BackupPanel(app: TetherApp, settings: Settings) {
    val state by app.backup.state.collectAsState()
    Spacer(Modifier.height(4.dp))
    Text("Backup", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
    val folder = settings.backupFolder
    if (folder == null) {
        Text("Escolha a pasta de backup em Ajustes → Salvamento (cartão SD ou pendrive OTG).", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        return
    }
    when (val s = state) {
        is BackupState.Running -> {
            Text("Copiando ${s.done + 1} de ${s.total}…", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { if (s.total == 0) 0f else (s.done + 1f) / s.total },
                modifier = Modifier.fillMaxWidth(),
                color = Tone.Info,
                trackColor = Tone.SurfaceHigh,
            )
            Text("Deixe o app aberto até terminar.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        }
        is BackupState.Finished -> {
            val ok = s.failed.isEmpty()
            Text(
                if (ok) "${s.verified} de ${s.total} arquivos copiados e conferidos." else "${s.verified} de ${s.total} conferidos. Faltaram:",
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) Tone.Accent else Tone.Error,
            )
            s.failed.take(5).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Tone.Error) }
            if (!ok) {
                OutlinedButton(onClick = { app.backup.start(settings.sessionKey, folder) }, modifier = Modifier.fillMaxWidth()) { Text("Tentar de novo o que faltou") }
            }
        }
        BackupState.Idle -> {
            val prominent = settings.backupOffer
            if (prominent) {
                Button(
                    onClick = { app.backup.start(settings.sessionKey, folder) },
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.SurfaceHigh, contentColor = Tone.Text),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Fazer backup da sessão") }
            } else {
                OutlinedButton(onClick = { app.backup.start(settings.sessionKey, folder) }, modifier = Modifier.fillMaxWidth()) { Text("Fazer backup da sessão") }
            }
            Text(app.store.describeTree(folder), style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Tone.TextMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
