package com.example.ui.delivery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.delivery.DeliveryItem
import com.example.core.delivery.DeliverySession
import com.example.core.delivery.DeliveryState
import com.example.core.delivery.DeliveryStatus
import com.example.ui.PhotoActions
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * ENTREGA LOCAL: the phone becomes the Wi-Fi and the gallery of the clients. Start and stop, the two
 * QR codes (join the Wi-Fi, open the photos), the clients with their codes, the queue and the history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveryScreen(app: TetherApp, actions: PhotoActions, onBack: (() -> Unit)?, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    val status by app.delivery.status.collectAsState()
    val online by app.delivery.online.collectAsState()
    val devices by app.delivery.devices.collectAsState()
    val sessions by app.delivery.sessions.collectAsState(initial = emptyList())
    val items by app.delivery.items.collectAsState(initial = emptyList())
    var openClient by remember { mutableStateOf<DeliverySession?>(null) }
    var newClient by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val s = settings ?: return

    val now = System.currentTimeMillis()
    val open = sessions.filter { it.isValid(now) }
    val closed = sessions.filterNot { it.isValid(now) }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) scope.launch { app.delivery.start() }
        else Toast.makeText(context, "Sem a permissão de Wi-Fi o celular não cria a rede. Use o roteador do celular em Ajustes da entrega.", Toast.LENGTH_LONG).show()
    }
    val start: () -> Unit = {
        val needed = permissionsFor(s.deliveryNetwork).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) scope.launch { app.delivery.start() } else askPermissions.launch(needed.toTypedArray())
    }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 1100.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
                Text("Entrega local", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes da entrega") }
            }

            if (!s.deliveryEnabled) {
                Intro(onEnable = { scope.launch { app.settings.set(SettingKeys.deliveryEnabled, true) } })
                return@Column
            }

            TwoUp(
                first = {
            StatusCard(
                status = status, online = online, devices = devices, network = networkLabel(s.deliveryNetwork),
                ready = items.count { it.state in DeliveryState.served && open.any { o -> o.id == it.sessionId } },
                onStart = start,
                onStop = { scope.launch { app.delivery.stop() } },
                onOpenPortal = { (status as? DeliveryStatus.Running)?.let { r -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } },
            )
                },
                second = {
            (status as? DeliveryStatus.Running)?.takeIf { !it.test }?.let { r ->
                DeliveryCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        r.wifiQr?.let { QrBlock("① Entrar no Wi-Fi", it, "${r.ssid}\nSenha: ${r.password}") }
                        QrBlock(if (r.wifiQr != null) "② Abrir as fotos" else "Abrir as fotos", r.url(), listOfNotNull(r.niceUrl, r.url()).joinToString("\nou "))
                    }
                    OutlinedButton(
                        onClick = { runCatching { PlateExport.share(context, PlateExport.Plate(r.ssid, r.password, r.url(), s.deliveryPhotographer)) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Print, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Placa para imprimir (PNG e PDF A6)")
                    }
                }
            }
                },
            )

            DeliveryCard {
                Text("Como as fotos chegam", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Manual", Icons.Outlined.TouchApp, s.deliveryMode != "auto") { scope.launch { app.settings.set(SettingKeys.deliveryMode, "manual") } }
                    ModeChip("Automático", Icons.Outlined.Bolt, s.deliveryMode == "auto") { scope.launch { app.settings.set(SettingKeys.deliveryMode, "auto") } }
                }
                Text(
                    if (s.deliveryMode == "auto") {
                        val active = open.firstOrNull { it.id == s.deliveryActiveSession }
                        when {
                            s.deliveryEventGallery -> "Cada foto nova vai para a galeria do evento."
                            active != null -> "Cada foto nova vai para ${active.client}. Troque o cliente da vez tocando nele."
                            else -> "Crie um cliente: cada foto nova vai para o cliente da vez."
                        }
                    } else {
                        "Você escolhe: na galeria, selecione as fotos e toque em Entregar."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                )
            }

            // Auto mode sends to the client of the moment; what it left out can still go to them.
            SkippedCard(app.delivery) { ids ->
                scope.launch {
                    val target = open.firstOrNull { it.id == s.deliveryActiveSession } ?: open.firstOrNull()
                    if (target == null) Toast.makeText(context, "Crie um cliente primeiro", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(context, "${app.delivery.send(target.id, ids)} foto(s) para ${target.client}", Toast.LENGTH_SHORT).show()
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clientes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(
                    onClick = { newClient = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Novo cliente")
                }
            }
            if (open.isEmpty()) {
                Text("Nenhum cliente aberto. Cada cliente recebe um código e um QR só dele.", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
            }
            open.forEach { session ->
                ClientRow(session, items.filter { it.sessionId == session.id }, active = session.id == s.deliveryActiveSession) { openClient = session }
            }

            val errors = items.count { it.state == DeliveryState.ERROR }
            val waiting = items.count { it.state == DeliveryState.WAITING || it.state == DeliveryState.PREPARING }
            if (errors > 0 || waiting > 0) {
                DeliveryCard {
                    Text("Fila", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            waiting.takeIf { it > 0 }?.let { "$it sendo preparadas" },
                            errors.takeIf { it > 0 }?.let { "$it com erro" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = if (errors > 0) Tone.Error else Tone.TextMuted,
                    )
                    if (errors > 0) {
                        OutlinedButton(onClick = { scope.launch { app.delivery.retryAll() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tentar de novo")
                        }
                    }
                }
            }

            if (closed.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { showHistory = !showHistory }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, null, tint = Tone.TextMuted)
                    Spacer(Modifier.width(12.dp))
                    Text("Histórico (${closed.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (showHistory) "Ocultar" else "Ver", color = Tone.TextMuted)
                }
                if (showHistory) closed.forEach { session ->
                    ClientRow(session, items.filter { it.sessionId == session.id }, active = false) { openClient = session }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    openClient?.let { current ->
        ClientSessionSheet(app, actions, sessions.firstOrNull { it.id == current.id } ?: current, onDismiss = { openClient = null })
    }
    if (newClient) {
        NewClientDialog(
            onDismiss = { newClient = false },
            onCreate = { name, phone ->
                newClient = false
                scope.launch { openClient = app.delivery.newClient(name, phone) }
            },
        )
    }
}

/** What the Wi-Fi Direct group needs from Android before it starts. */
fun permissionsFor(network: String): List<String> = when {
    network != "direct" -> emptyList()
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

fun networkLabel(id: String) = when (id) {
    "hotspot" -> "Roteador do celular"
    "wifi" -> "Wi-Fi do local"
    else -> "Wi-Fi Direct"
}

@Composable
private fun Intro(onEnable: () -> Unit) {
    DeliveryCard {
        Icon(Icons.Outlined.WifiTethering, null, tint = Tone.Accent, modifier = Modifier.size(36.dp))
        Text("Entregue as fotos pelo Wi-Fi", style = MaterialTheme.typography.titleLarge)
        listOf(
            "O celular cria uma rede Wi-Fi e uma página com as fotos de cada cliente.",
            "O cliente aponta a câmera para o QR, entra e salva no próprio celular. Sem internet e sem instalar nada.",
            "Cada cliente tem um código: ninguém vê as fotos de outra pessoa.",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted) }
        Button(
            onClick = onEnable,
            colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Ligar a entrega local") }
        Text("Desligada, nada disso roda e nenhuma permissão nova é pedida.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
    }
}

@Composable
internal fun StatusCard(
    status: DeliveryStatus,
    online: Int,
    devices: Int?,
    network: String,
    ready: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenPortal: () -> Unit,
    /** What "on the air" is called on this tool's screen. */
    liveTitle: String = "Entrega ativa",
) {
    val running = status as? DeliveryStatus.Running
    val (dot, title, detail) = when (status) {
        is DeliveryStatus.Running -> Triple(Tone.Accent, if (status.test) "Teste de entrega no ar" else liveTitle, "${status.network} · ${status.niceUrl ?: status.url()}")
        DeliveryStatus.Starting -> Triple(Tone.Info, "Ligando…", network)
        is DeliveryStatus.Failed -> Triple(Tone.Error, "Não ligou", status.reason)
        DeliveryStatus.Off -> Triple(Tone.TextMuted, "Parada", "$network · toque em Iniciar quando for entregar")
    }
    DeliveryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = if (status is DeliveryStatus.Failed) Tone.Error else Tone.TextMuted)
        if (running != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat(Icons.Outlined.Devices, devices?.let { "$it na rede" } ?: "$online no portal")
                if (devices != null) Stat(Icons.Outlined.Person, "$online no portal")
                Stat(Icons.Outlined.Wifi, "$ready ${if (ready == 1) "foto pronta" else "fotos prontas"}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running != null) {
                OutlinedButton(onClick = onOpenPortal, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.OpenInBrowser, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir portal", maxLines = 1)
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.StopCircle, null, tint = Tone.Error)
                    Spacer(Modifier.width(6.dp))
                    Text("Parar", color = Tone.Error, maxLines = 1)
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = status != DeliveryStatus.Starting,
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.WifiTethering, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (status is DeliveryStatus.Failed) "Tentar de novo" else "Iniciar entrega")
                }
            }
        }
    }
}

@Composable
internal fun Stat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Tone.TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        com.example.ui.components.RollingText(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun QrBlock(title: String, qr: String, caption: String) {
    // Two side by side on a phone: the client scans one, then the other, without scrolling.
    Column(Modifier.width(146.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        QrImage(qr, 134.dp, contentDescription = title)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
    }
}

@Composable
private fun ModeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tone.SurfaceHigh, selectedLabelColor = Tone.Text, selectedLeadingIconColor = Tone.Accent),
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun ClientRow(session: DeliverySession, items: List<DeliveryItem>, active: Boolean, onClick: () -> Unit) {
    val ready = items.count { it.state in DeliveryState.served }
    val downloaded = items.count { it.downloads > 0 }
    val valid = session.isValid(System.currentTimeMillis())
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Tone.Surface).clickable(onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Person, null, tint = if (active) Tone.Accent else Tone.TextMuted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(session.client + if (session.event) " · evento" else "", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (valid) "código ${session.code}" else "encerrado") + " · ${items.size} fotos · $ready prontas · $downloaded baixadas" +
                    if (active) " · da vez" else "",
                style = MaterialTheme.typography.bodySmall, color = if (active) Tone.Accent else Tone.TextMuted,
                maxLines = 2,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Tone.TextMuted)
    }
}

@Composable
private fun NewClientDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        title = { Text("Novo cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nome") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(phone, { phone = it.take(20) }, label = { Text("WhatsApp (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Text("Ele vira o cliente da vez: no modo automático, as fotos novas vão para ele.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, phone.ifBlank { null }) }, enabled = name.isNotBlank()) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun DeliveryCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Tone.Surface).padding(16.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}


/**
 * Two cards side by side when the screen is wide (phone lying down, tablet), one under the other
 * when it is narrow. Used for the status and the QR codes, which belong together.
 */
@Composable
internal fun TwoUp(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 680.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { first() }
                Column(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                first()
                second()
            }
        }
    }
}
