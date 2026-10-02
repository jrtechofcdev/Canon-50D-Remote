package com.example.ui.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TetherApp
import com.example.core.data.Settings
import com.example.core.delivery.DeliveryItem
import com.example.core.delivery.DeliverySession
import com.example.core.delivery.DeliveryState
import com.example.core.delivery.DeliveryStatus
import com.example.core.messages.MessageData
import com.example.core.messages.MessageTemplates
import com.example.ui.PhotoActions
import com.example.ui.photos.messages
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/** One client: their code and QR (straight into their photos), what is queued for them, and their access. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSessionSheet(app: TetherApp, actions: PhotoActions, session: DeliverySession, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val status by app.delivery.status.collectAsState()
    val settings by app.settings.settings.collectAsState(initial = null)
    val all by app.delivery.items.collectAsState(initial = emptyList())
    val items = all.filter { it.sessionId == session.id }
    val running = status as? DeliveryStatus.Running
    val valid = session.isValid(System.currentTimeMillis())
    val active = settings?.deliveryActiveSession == session.id

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(session.client, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    !valid -> "Acesso encerrado"
                    session.event -> "Galeria do evento: todos com o código veem"
                    active -> "Cliente da vez: as fotos do modo automático vêm para cá"
                    else -> "Só esta pessoa vê estas fotos"
                },
                style = MaterialTheme.typography.bodySmall, color = if (active) Tone.Accent else Tone.TextMuted,
            )
            if (valid) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (running != null && !running.test) {
                        QrImage(running.url(session.token), 148.dp, contentDescription = "QR das fotos de ${session.client}")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Código", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
                        Text(session.code.chunked(3).joinToString(" "), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                        Text(
                            running?.let { "O cliente aponta a câmera para o QR, ou abre ${it.url()} e digita o código." }
                                ?: "Inicie a entrega para mostrar o QR.",
                            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!session.event) {
                        OutlinedButton(onClick = { scope.launch { app.delivery.setActive(session.id) } }, enabled = !active, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(if (active) "Cliente da vez" else "Tornar da vez", maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = { settings?.let { s -> scope.launch { sendAccess(actions, s, session, running) } } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp", maxLines = 1)
                    }
                }
            }

            val ready = items.count { it.state in DeliveryState.served }
            val downloaded = items.count { it.downloads > 0 }
            Text(
                "${items.size} ${if (items.size == 1) "foto" else "fotos"} · $ready prontas · $downloaded baixadas" +
                    (items.count { it.picked }.takeIf { it > 0 }?.let { " · $it favoritas" } ?: ""),
                style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted, modifier = Modifier.padding(top = 8.dp),
            )
            if (items.isEmpty()) Text("Nenhuma foto ainda. Na galeria, selecione as fotos e toque em Entregar.", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
            items.forEach { item -> QueueRow(item, onRetry = { scope.launch { app.delivery.retry(item.id) } }, onRemove = { scope.launch { app.delivery.removeItem(item) } }) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (valid) {
                    OutlinedButton(onClick = { scope.launch { app.delivery.closeClient(session.id); onDismiss() } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Encerrar acesso", maxLines = 1)
                    }
                }
                OutlinedButton(onClick = { scope.launch { app.delivery.deleteClient(session.id); onDismiss() } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = Tone.Error)
                    Spacer(Modifier.width(6.dp))
                    Text("Apagar", color = Tone.Error, maxLines = 1)
                }
            }
        }
    }
}

/** One photo in a client's queue: ● baixada · ✓ pronta · ◐ preparando · ○ aguardando · ⚠ erro. */
@Composable
fun QueueRow(item: DeliveryItem, onRetry: () -> Unit, onRemove: () -> Unit) {
    val (icon, color, label) = stateLook(item)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        }
        if (item.picked) Icon(Icons.Outlined.Favorite, "Favorita do cliente", tint = Tone.Accent, modifier = Modifier.size(18.dp))
        if (item.state == DeliveryState.ERROR) IconButton(onClick = onRetry) { Icon(Icons.Outlined.Refresh, "Tentar de novo") }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Tirar da entrega", tint = Tone.TextMuted) }
    }
}

@Composable
private fun stateLook(item: DeliveryItem): Triple<ImageVector, androidx.compose.ui.graphics.Color, String> = when (item.state) {
    DeliveryState.DOWNLOADED -> Triple(Icons.Outlined.TaskAlt, Tone.Accent, if (item.downloads == 1) "Baixada" else "Baixada ${item.downloads} vezes")
    DeliveryState.READY -> Triple(Icons.Outlined.CheckCircle, Tone.Info, "Pronta no portal · " + "%.1f MB".format(item.size / 1_048_576.0))
    DeliveryState.PREPARING -> Triple(Icons.Outlined.Sync, Tone.Info, "Preparando")
    DeliveryState.ERROR -> Triple(Icons.Outlined.ErrorOutline, Tone.Error, item.error ?: "Erro")
    else -> Triple(Icons.Outlined.HourglassEmpty, Tone.TextMuted, "Aguardando")
}

/** The "Fotos no Wi-Fi" message, filled with this client's code and the network, to their WhatsApp. */
suspend fun sendAccess(actions: PhotoActions, s: Settings, session: DeliverySession, running: DeliveryStatus.Running?) {
    val template = s.messages().firstOrNull { it.id == "wifi" }?.text ?: return
    val text = MessageTemplates.fill(
        template,
        MessageData(
            client = session.client, count = 0, date = System.currentTimeMillis(), pix = s.pixKey,
            code = session.code, wifi = running?.ssid ?: s.deliverySsid, wifiPassword = running?.password ?: s.deliveryPassword,
            link = running?.url() ?: "http://192.168.49.1:8080",
        ),
    )
    val phone = session.phone
    if (phone == null || !actions.openWhatsAppChat(phone, text)) actions.sendMessage(text, emptyList(), null, s)
}
