package com.example.ui.church

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.TetherApp
import com.example.core.church.ChurchEvent
import com.example.core.church.ChurchRules
import com.example.core.church.ChurchVia
import com.example.core.church.Congregation
import com.example.core.delivery.DeliveryStatus
import com.example.ui.components.JrButton
import com.example.ui.components.JrButtonKind
import com.example.ui.components.JrChip
import com.example.ui.components.ScreenHeader
import com.example.ui.delivery.DeliveryCard
import com.example.ui.delivery.QrBlock
import com.example.ui.delivery.StatusCard
import com.example.ui.delivery.TwoUp
import com.example.ui.delivery.networkLabel
import com.example.ui.delivery.permissionsFor
import com.example.ui.theme.Space
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ENTREGA DA IGREJA: one open event at a time. The media team comes in through the link, the QR or the
 * code, types name and role, and sees the photos as they are taken. Here the photographer runs the event:
 * the network, who comes in, what each person may see and download, the "pronta" look and the log.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChurchScreen(app: TetherApp, onBack: (() -> Unit)?, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = app.church.repo
    val settings by app.settings.settings.collectAsState(initial = null)
    val event by repo.dao.observeOpenEvent().collectAsState(initial = null)
    val events by repo.dao.observeEvents().collectAsState(initial = emptyList())
    val congregations by repo.dao.observeCongregations().collectAsState(initial = emptyList())
    val status by app.church.status.collectAsState()
    val online by app.church.server.online.collectAsState()
    val s = settings ?: return
    var managing by remember { mutableStateOf(false) }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) scope.launch { app.church.start() }
        else app.notice("Sem a permissão de Wi-Fi o celular não cria a rede. Permita, ou use o Wi-Fi da igreja em Ajustes da entrega da igreja.")
    }
    val start: () -> Unit = {
        val needed = permissionsFor(s.churchNetwork).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) scope.launch { app.church.start() } else askPermissions.launch(needed.toTypedArray())
    }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
            ScreenHeader(
                title = "Entrega da igreja",
                onBack = onBack,
            ) {
                IconButton(onClick = { managing = true }) { Icon(Icons.Outlined.Groups, contentDescription = "Congregações", tint = Tone.TextMuted) }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes da entrega da igreja", tint = Tone.TextMuted) }
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.l, vertical = Space.s),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val e = event
                val eventPhotos by remember(e?.id) { e?.let { repo.dao.observePhotos(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
                if (e == null) {
                    NewEventCard(app, congregations)
                    PastEvents(app, events.filter { !it.isOpen })
                } else {
                    EventCard(app, e)
                    TwoUp(
                        first = {
                            StatusCard(
                                status = status, online = online, devices = null, network = networkLabel(s.churchNetwork),
                                ready = eventPhotos.count { !it.hidden },
                                onStart = start,
                                onStop = { scope.launch { app.church.stop() } },
                                onOpenPortal = { app.church.eventUrl(e, ChurchVia.LINK)?.let { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } },
                                liveTitle = "Igreja no ar",
                            )
                        },
                        second = { AccessCard(app, e, status) },
                    )
                    EventPhotosCard(app, e)
                    DefaultPermsCard(app, e)
                    PeopleCard(app, e)
                    ReadyCard(app, e, s.enhanceOnChurch)
                    ThemeCard(app, e)
                    LogCard(app, e)
                    CloseEventCard(app, e)
                }
                Spacer(Modifier.padding(bottom = 88.dp))
            }
        }
    }
    if (managing) CongregationsSheet(app, onDismiss = { managing = false })
}

// ---- no event open --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewEventCard(app: TetherApp, congregations: List<Congregation>) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var congregation by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    DeliveryCard {
        Icon(Icons.Outlined.Church, null, tint = Tone.TextMuted)
        Text("Novo evento", style = MaterialTheme.typography.titleLarge)
        Text(
            "Culto, cruzada ou festa. A mídia da igreja entra pelo QR, link ou código, informa nome e cargo, e vê as fotos enquanto você fotografa.",
            style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted,
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            label = { Text("Nome do evento") }, placeholder = { Text("Culto de domingo") },
            singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Congregação", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            JrChip("Nenhuma", congregation == null, onClick = { congregation = null })
            congregations.forEach { c -> JrChip(c.name, congregation == c.id, onClick = { congregation = c.id }) }
            JrChip("Nova", false, onClick = { adding = true }, icon = Icons.Outlined.Add)
        }
        Text(
            "O evento leva as cores, a frase, a logo e o tratamento da congregação. Dá para mudar depois só para este evento.",
            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
        )
        JrButton(
            "Abrir evento",
            onClick = { scope.launch { app.church.repo.newEvent(name, congregation); name = "" } },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (adding) CongregationsSheet(app, onDismiss = { adding = false }, editing = 0L, onSaved = { congregation = it })
}

@Composable
private fun PastEvents(app: TetherApp, past: List<ChurchEvent>) {
    if (past.isEmpty()) return
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<ChurchEvent?>(null) }
    var cleaned by remember { mutableStateOf(0) }
    DeliveryCard {
        Text("Eventos anteriores", style = MaterialTheme.typography.titleMedium)
        past.take(20).forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(e.congregation, day(e.date)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
                // Copies left from an event closed without cleaning: one tap frees them.
                val size by produceState(0L, e.id, cleaned) { value = app.church.filesSize(e.id) }
                if (size > 0) TextButton(onClick = { scope.launch { app.church.clearFiles(e.id); cleaned++ } }) { Text("Liberar ${megabytes(size)}") }
                TextButton(onClick = { scope.launch { app.church.repo.open(e.id) } }) { Text("Reabrir") }
                IconButton(onClick = { deleting = e }) { Icon(Icons.Outlined.Delete, contentDescription = "Apagar ${e.name}", tint = Tone.TextMuted) }
            }
        }
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Apagar ${e.name}?") },
            text = { Text("Some o evento, as pessoas e o log. As fotos continuam no app.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.church.repo.deleteEvent(e.id); app.church.clearFiles(e.id) }; deleting = null }) { Text("Apagar", color = Tone.Error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

// ---- the open event -------------------------------------------------------------------------

/** How people come in: the QR codes, the link, the code, and a switch for each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessCard(app: TetherApp, e: ChurchEvent, status: DeliveryStatus) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = app.church.repo
    var typing by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    DeliveryCard {
        Text("Entrada", style = MaterialTheme.typography.titleMedium)
        val running = status as? DeliveryStatus.Running
        if (running != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                running.wifiQr?.let { QrBlock("① Entrar no Wi-Fi", it, "${running.ssid}\nSenha: ${running.password}") }
                if (e.qrOn) app.church.eventUrl(e, ChurchVia.QR)?.let { QrBlock(if (running.wifiQr != null) "② Ver as fotos" else "Ver as fotos", it, listOfNotNull(running.niceUrl, running.url()).joinToString("\nou ")) }
            }
            if (e.linkOn) app.church.eventUrl(e, ChurchVia.LINK)?.let { link ->
                JrButton(
                    "Mandar o link", kind = JrButtonKind.Secondary, icon = Icons.Outlined.Link,
                    onClick = {
                        val text = "*${e.name}*\nFotos ao vivo: $link" + if (e.codeOn) "\nCódigo: *${e.code}*" else ""
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Mandar o link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            JrButton(
                "Placa para imprimir", kind = JrButtonKind.Secondary, icon = Icons.Outlined.Print,
                onClick = { runCatching { com.example.ui.delivery.PlateExport.share(context, churchPlate(app, e, running)) }.onFailure { app.notice("A placa não foi criada: ${it.message}") } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!e.linkOn && !e.qrOn) Text("Link e QR desligados: só entra quem digitar o endereço ${running.url()}", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        } else {
            Text("Inicie a entrega para mostrar os QR codes.", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
        }
        AccessSwitch(Icons.Outlined.QrCode2, "Entrar pelo QR", e.qrOn) { scope.launch { repo.setAccess(e.id, e.linkOn, it, e.codeOn) } }
        AccessSwitch(Icons.Outlined.Link, "Entrar pelo link", e.linkOn) { scope.launch { repo.setAccess(e.id, it, e.qrOn, e.codeOn) } }
        AccessSwitch(Icons.Outlined.Key, "Pedir o código", e.codeOn) { scope.launch { repo.setAccess(e.id, e.linkOn, e.qrOn, it) } }
        if (e.codeOn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.code, style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum", letterSpacing = MaterialTheme.typography.displaySmall.letterSpacing), color = Tone.Text, modifier = Modifier.weight(1f))
                TextButton(onClick = { typing = true }) { Text("Digitar") }
                TextButton(onClick = { resetting = true }) { Text("Trocar") }
            }
            Text("Um código para todos. Quem já entrou continua dentro, a não ser que você troque e tire todos.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        } else {
            Text("Sem código: quem chegar pela entrada ligada já informa nome e cargo e entra.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        }
    }
    if (typing) TextDialog(
        title = "Código do evento", label = "4 números", initial = e.code, numbers = true,
        onDismiss = { typing = false },
        onSave = { code -> typing = false; if (ChurchRules.validCode(code)) scope.launch { repo.resetCode(e.id, code) } else app.notice("O código precisa ter 4 números.") },
    )
    if (resetting) {
        var kickAll by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text("Trocar o código?") },
            text = {
                Column {
                    Text("Um código novo é gerado. Passe o novo para quem ainda vai entrar.")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.s)) {
                        Checkbox(checked = kickAll, onCheckedChange = { kickAll = it })
                        Text("Tirar todos que já entraram (precisam do código novo)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { resetting = false; scope.launch { repo.resetCode(e.id, kickAll = kickAll) } }) { Text("Trocar") } },
            dismissButton = { TextButton(onClick = { resetting = false }) { Text("Cancelar") } },
        )
    }
}

/** The printed plate of the event: Wi-Fi, the page (QR way in, or the address) and the code when it is asked. */
internal fun churchPlate(app: TetherApp, e: ChurchEvent, running: DeliveryStatus.Running): com.example.ui.delivery.PlateExport.Plate {
    val url = (if (e.qrOn) app.church.eventUrl(e, ChurchVia.QR) else null) ?: running.niceUrl ?: running.url()
    val accent = runCatching { android.graphics.Color.parseColor(com.example.core.church.ChurchTheme.color(e.accent) ?: com.example.core.church.ChurchTheme.ACCENT) }.getOrDefault(0xFF00C987.toInt())
    return com.example.ui.delivery.PlateExport.Plate(
        running.ssid, running.password, url, "Fotos por Jr_Tech_OFC",
        typed = running.niceUrl ?: running.url(),
        church = com.example.ui.delivery.PlateExport.Church(e.name, e.congregation, accent, e.code.takeIf { e.codeOn }),
    )
}

@Composable
internal fun AccessSwitch(icon: ImageVector, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (on) Tone.Accent else Tone.TextMuted)
        Spacer(Modifier.width(Space.m))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Tone.Accent, checkedThumbColor = Tone.OnAccent))
    }
}

/** The look of the "pronta" version: a preset and Melhorar. The original and the RAW never change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyCard(app: TetherApp, e: ChurchEvent, enhanceEverywhere: Boolean) {
    val scope = rememberCoroutineScope()
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    DeliveryCard {
        Text("Versão pronta", style = MaterialTheme.typography.titleMedium)
        Text("É a foto com o tratamento escolhido aqui. A original e o RAW saem como a câmera fez.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            JrChip("O da foto", e.presetId == null, onClick = { scope.launch { app.church.repo.updateEvent(e.copy(presetId = null, adjust = null)) } })
            presets.forEach { p -> JrChip(p.name, e.presetId == p.id, onClick = { scope.launch { app.church.repo.updateEvent(e.copy(presetId = p.id, adjust = null)) } }) }
        }
        AccessSwitch(Icons.Outlined.AutoFixHigh, "Melhorar (menos granulado)", e.enhanceReady || enhanceEverywhere) { on ->
            if (enhanceEverywhere && !on) app.notice("O Melhorar está ligado para a igreja em Ajustes → Melhorar.")
            else scope.launch { app.church.repo.updateEvent(e.copy(enhanceReady = on)) }
        }
    }
}

/** The event itself: name, congregation and date, with Editar for when something was typed wrong. */
@Composable
private fun EventCard(app: TetherApp, e: ChurchEvent) {
    var editing by remember { mutableStateOf(false) }
    DeliveryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(e.congregation, day(e.date)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
            }
            JrButton("Editar", onClick = { editing = true }, kind = JrButtonKind.Secondary, icon = Icons.Outlined.Edit)
        }
    }
    if (editing) EditEventDialog(app, e) { editing = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEventDialog(app: TetherApp, e: ChurchEvent, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(e.name) }
    var date by remember { mutableStateOf(e.date) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Editar evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) },
                    label = { Text("Nome do evento") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Data", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
                        Text(day(date), style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = { picking = true }) { Text("Trocar") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { app.church.repo.editEvent(e.id, name, date) }; onDone() }, enabled = name.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } },
    )
    if (picking) {
        // The picker works in UTC days; the event keeps its own time of day on the chosen day.
        val state = rememberDatePickerState(initialSelectedDateMillis = localDayAsUtc(date))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { date = utcDayAsLocal(it, date) }; picking = false }) { Text("Ok") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

private fun localDayAsUtc(ms: Long): Long {
    val local = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear(); set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcDayAsLocal(utcDay: Long, keepTimeOf: Long): Long {
    val day = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
    return java.util.Calendar.getInstance().apply {
        timeInMillis = keepTimeOf
        set(day.get(java.util.Calendar.YEAR), day.get(java.util.Calendar.MONTH), day.get(java.util.Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@Composable
private fun CloseEventCard(app: TetherApp, e: ChurchEvent) {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var clean by remember { mutableStateOf(true) }
    val size by produceState(0L, asking) { if (asking) value = app.church.filesSize(e.id) }
    JrButton("Encerrar evento", onClick = { asking = true }, kind = JrButtonKind.Secondary, icon = Icons.Outlined.EventBusy, modifier = Modifier.fillMaxWidth())
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text("Encerrar ${e.name}?") },
        text = {
            Column {
                Text("Ninguém entra mais e quem está na página volta para a entrada. O evento fica na lista para reabrir.")
                if (size > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.s)) {
                    Checkbox(checked = clean, onCheckedChange = { clean = it })
                    Text("Apagar as cópias preparadas para a mídia (${megabytes(size)}). As fotos do app não são tocadas.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                asking = false
                scope.launch {
                    app.church.repo.close(e.id)
                    app.church.stop()
                    if (clean) app.church.clearFiles(e.id)
                }
            }) { Text("Encerrar") }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancelar") } },
    )
}

// ---- small pieces ---------------------------------------------------------------------------

@Composable
internal fun TextDialog(title: String, label: String, initial: String, numbers: Boolean = false, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (numbers) it.filter(Char::isDigit).take(4) else it.take(40) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = if (numbers) KeyboardOptions(keyboardType = KeyboardType.NumberPassword) else KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

internal fun megabytes(bytes: Long): String = if (bytes < 1_000_000) "menos de 1 MB" else "${bytes / 1_000_000} MB"

internal fun day(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(ms))
internal fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(ms))

