package com.example.ui.delivery

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.delivery.DeliveryItem
import com.example.core.delivery.DeliveryState
import com.example.core.delivery.DeliveryStatus
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FOTOGRAFIA EXPRESS: one client at a time, no code. The photos arrive in the portal as marked previews;
 * after payment the photographer releases the chosen ones, and "Finalizar e limpar" readies the next client.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ExpressScreen(app: TetherApp, onBack: (() -> Unit)?, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    val status by app.express.status.collectAsState()
    val online by app.express.online.collectAsState()
    val devices by app.express.devices.collectAsState()
    val sessions by app.express.sessions.collectAsState(initial = emptyList())
    val allItems by app.express.items.collectAsState(initial = emptyList())
    val selected = remember { mutableStateListOf<Long>() }
    var finishing by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val s = settings ?: return

    var picking by remember { mutableStateOf(false) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(s.expressEnabled) {
        if (!s.expressEnabled) return@LaunchedEffect
        app.express.expressSession()
        // "Ligar o Wi-Fi ao abrir": the network comes up with the tab, no extra tap.
        if (s.expressAutoStart && app.express.status.value is DeliveryStatus.Off) {
            val needed = permissionsFor(s.expressNetwork).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isEmpty()) app.express.start()
        }
    }

    val now = System.currentTimeMillis()
    val current = sessions.firstOrNull { it.express && it.isValid(now) }
    val items = allItems.filter { it.sessionId == current?.id }
    val history = sessions.filter { it.express && !it.isValid(now) }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) scope.launch { app.express.start() }
        else Toast.makeText(context, "Sem a permissão de Wi-Fi o celular não cria a rede. Use o roteador do celular em Ajustes da entrega.", Toast.LENGTH_LONG).show()
    }
    val start: () -> Unit = {
        val needed = permissionsFor(s.expressNetwork).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) scope.launch { app.express.start() } else askPermissions.launch(needed.toTypedArray())
    }
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_SHORT).show()

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
                Text("Fotografia Express", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes do Express") }
            }

            if (!s.expressEnabled) {
                DeliveryCard {
                    Icon(Icons.Outlined.Collections, null, tint = Tone.Accent, modifier = Modifier.size(36.dp))
                    Text("Fotografou, mostrou, entregou", style = MaterialTheme.typography.titleLarge)
                    listOf(
                        "Um cliente por vez e sem código: ele entra no Wi-Fi e as fotos já aparecem.",
                        "No modo prévia, ele só vê as fotos com marca d'água e não consegue baixar.",
                        "Depois do pagamento, você libera as fotos escolhidas e ele salva sem marca.",
                        "No fim, um botão limpa tudo para o próximo cliente.",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted) }
                    Button(
                        onClick = { scope.launch { app.settings.set(SettingKeys.expressEnabled, true) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Ligar a Fotografia Express") }
                }
                return@Column
            }

            StatusCard(
                status = status, online = online, devices = devices, network = networkLabel(s.expressNetwork),
                ready = items.count { it.state in DeliveryState.served },
                onStart = start,
                onStop = { scope.launch { app.express.stop() } },
                onOpenPortal = { (status as? DeliveryStatus.Running)?.let { r -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } },
                liveTitle = "Express no ar",
            )

            (status as? DeliveryStatus.Running)?.takeIf { !it.test }?.let { r ->
                DeliveryCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        r.wifiQr?.let { QrBlock("① Entrar no Wi-Fi", it, "${r.ssid}\nSenha: ${r.password}") }
                        QrBlock(if (r.wifiQr != null) "② Ver as fotos" else "Ver as fotos", r.url(), listOfNotNull(r.niceUrl, r.url()).joinToString("\nou ") + "\nSem código")
                    }
                    OutlinedButton(
                        onClick = { runCatching { PlateExport.share(context, PlateExport.Plate(r.ssid, r.password, r.url(), s.expressPhotographer, express = true)) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Print, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Placa para imprimir")
                    }
                }
            }

            SkippedCard(app.express) { ids ->
                scope.launch { val n = app.express.sendToExpress(ids); toast("$n ${if (n == 1) "foto mandada" else "fotos mandadas"}") }
            }

            // The switch that decides what the client can take.
            DeliveryCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (s.expressPreview) Icons.Outlined.Lock else Icons.Outlined.LockOpen, null, tint = if (s.expressPreview) Tone.Accent else Tone.TextMuted)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Modo prévia", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (s.expressPreview) "O cliente vê com marca d'água e só baixa as fotos que você liberar."
                            else "Desligado: o cliente baixa todas as fotos sem marca.",
                            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                        )
                    }
                    Switch(
                        checked = s.expressPreview,
                        onCheckedChange = { scope.launch { app.settings.set(SettingKeys.expressPreview, it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Tone.Accent, checkedThumbColor = Tone.OnAccent),
                    )
                }
            }

            val released = items.count { it.released }
            val picked = items.filter { it.picked }
            DeliveryCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            current?.client?.takeUnless { it == "Express" }?.let { "Fotos de $it" } ?: "Fotos do cliente",
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${items.size} ${if (items.size == 1) "foto" else "fotos"} · $released liberadas · ${picked.size} favoritas do cliente",
                            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                        )
                        // What to charge: the photos marked here, or the client's hearts (best price for them).
                        val chargeCount = if (selected.isNotEmpty()) selected.size else picked.count { !it.released }
                        com.example.core.payments.PricePlan.quote(s, chargeCount, items.size)?.let { q ->
                            Text(
                                (if (selected.isNotEmpty()) "${selected.size} marcadas: " else "$chargeCount favoritas: ") +
                                    com.example.core.payments.PricePlan.money(q.cents) + " (${q.detail})",
                                style = MaterialTheme.typography.labelLarge, color = Tone.Accent,
                            )
                        }
                    }
                    Button(
                        onClick = { picking = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mandar fotos")
                    }
                }
                if (items.isEmpty()) {
                    Text(
                        if (s.expressAuto) "Cada foto nova da câmera entra aqui sozinha. Para mandar outras, toque em Mandar fotos." else "Toque em Mandar fotos, ou na galeria selecione e toque em Express.",
                        style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted,
                    )
                } else {
                    Text(
                        if (selected.isEmpty()) "Toque nas fotos pagas para marcar." else "${selected.size} marcadas",
                        style = MaterialTheme.typography.labelLarge, color = if (selected.isEmpty()) Tone.TextMuted else Tone.Accent,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach { item ->
                            ExpressTile(item, item.id in selected, previewOn = s.expressPreview) {
                                if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected.isNotEmpty()) {
                            Button(
                                onClick = { scope.launch { app.express.release(selected.toList()); toast("${selected.size} liberadas"); selected.clear() } },
                                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Outlined.LockOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Liberar ${selected.size}")
                            }
                            OutlinedButton(onClick = { scope.launch { app.express.release(selected.toList(), on = false); selected.clear() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Bloquear de novo")
                            }
                        } else {
                            if (picked.any { !it.released }) {
                                OutlinedButton(onClick = { scope.launch { app.express.release(picked.map { it.id }); toast("Favoritas liberadas") } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Icon(Icons.Outlined.Favorite, null, tint = Tone.Accent)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Liberar favoritas (${picked.size})")
                                }
                            }
                            if (s.pixKey.isNotBlank()) {
                                OutlinedButton(onClick = { charging = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Icon(Icons.Outlined.QrCode2, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cobrar")
                                }
                            }
                            OutlinedButton(onClick = { scope.launch { app.express.releaseAll(); toast("Todas liberadas") } }, enabled = released < items.size, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Outlined.DoneAll, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Liberar todas")
                            }
                        }
                    }
                }
            }

            OutlinedButton(onClick = { finishing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.CleaningServices, null)
                Spacer(Modifier.width(8.dp))
                Text("Finalizar e limpar para o próximo cliente")
            }

            if (history.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { showHistory = !showHistory }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, null, tint = Tone.TextMuted)
                    Spacer(Modifier.width(12.dp))
                    Text("Clientes atendidos (${history.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (showHistory) "Ocultar" else "Ver", color = Tone.TextMuted)
                }
                if (showHistory) {
                    val format = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }
                    history.forEach { h ->
                        val its = allItems.filter { it.sessionId == h.id }
                        Text(
                            "${h.client} · ${format.format(Date(h.createdAt))} · ${its.size} fotos · ${its.count { it.released }} liberadas",
                            style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted,
                            modifier = Modifier.padding(start = 36.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (charging) {
        val count = if (selected.isNotEmpty()) selected.size else items.count { it.picked && !it.released }
        val quote = com.example.core.payments.PricePlan.quote(s, count, items.size)
        val code = com.example.core.payments.PricePlan.pix(s.copy(payMethods = "pix"), quote?.cents)
        AlertDialog(
            onDismissRequest = { charging = false },
            containerColor = Tone.Surface,
            title = { Text(quote?.let { com.example.core.payments.PricePlan.money(it.cents) } ?: "Pix") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        quote?.let { "$count ${if (count == 1) "foto" else "fotos"} · ${it.detail}" } ?: "Sem valor definido: o cliente digita o valor no banco.",
                        style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                    )
                    code?.let { QrImage(it, 240.dp, contentDescription = "QR do Pix") }
                    Text("O cliente lê com o app do banco. Depois do pagamento, toque em Liberar.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
            },
            confirmButton = { TextButton(onClick = { charging = false }) { Text("Fechar") } },
        )
    }

    if (picking) {
        ExpressPickSheet(app, alreadyIn = items.map { it.photoId }.toSet(), onDismiss = { picking = false }) { ids ->
            picking = false
            scope.launch {
                val added = app.express.sendToExpress(ids)
                toast(if (added == 0) "Essas fotos já estavam no Express" else "$added ${if (added == 1) "foto mandada" else "fotos mandadas"} ao cliente")
            }
        }
    }

    if (finishing) {
        var name by remember { mutableStateOf(current?.client?.takeUnless { it == "Express" } ?: "") }
        AlertDialog(
            onDismissRequest = { finishing = false },
            containerColor = Tone.Surface,
            icon = { Icon(Icons.Outlined.CleaningServices, null) },
            title = { Text("Finalizar este cliente?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O portal fica vazio e as cópias da entrega são apagadas. As fotos originais continuam na galeria.", color = Tone.TextMuted)
                    OutlinedTextField(
                        name, { name = it.take(40) }, label = { Text("Nome do cliente (para o histórico)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    finishing = false
                    selected.clear()
                    scope.launch { app.express.finishExpress(name); toast("Pronto para o próximo cliente") }
                }) { Text("Finalizar e limpar") }
            },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text("Cancelar") } },
        )
    }
}

/** One photo of the Express client: picture, liberada or prévia, the client's heart, and the mark selection. */
@Composable
private fun ExpressTile(item: DeliveryItem, selected: Boolean, previewOn: Boolean, onClick: () -> Unit) {
    val ready = item.state in DeliveryState.served && item.path != null
    Box(
        Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)).background(Tone.SurfaceHigh)
            .then(if (selected) Modifier.border(3.dp, Tone.Accent, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(enabled = ready, onClick = onClick),
    ) {
        if (ready) {
            AsyncImage(model = File(item.path!!), contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.HourglassEmpty, "Preparando", tint = Tone.TextMuted, modifier = Modifier.align(Alignment.Center))
        }
        val (label, color) = when {
            item.released || !previewOn -> "Liberada" to Tone.Accent
            else -> "Prévia" to Tone.TextMuted
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(999.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (label == "Liberada") Icons.Outlined.LockOpen else Icons.Outlined.Visibility, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
        if (item.picked) Icon(Icons.Filled.Favorite, "Favorita do cliente", tint = Tone.Accent, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp))
        if (selected) {
            Box(Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp).clip(CircleShape).background(Tone.Accent))
        }
    }
}
