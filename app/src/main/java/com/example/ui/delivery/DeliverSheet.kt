package com.example.ui.delivery

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.delivery.DeliverySession
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * "Entregar": the chosen photos go to a client of the local delivery. Pick one of the open clients
 * (the active one comes first) or type a new name; the photos are prepared one by one in the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliverSheet(app: TetherApp, photoIds: List<Long>, onDismiss: () -> Unit, onOpenDelivery: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    val sessions by app.delivery.sessions.collectAsState(initial = emptyList())
    val now = remember { System.currentTimeMillis() }
    val open = sessions.filter { it.isValid(now) }.sortedByDescending { it.id == settings?.deliveryActiveSession }
    var name by remember { mutableStateOf("") }
    val count = photoIds.size


    fun send(session: DeliverySession) = scope.launch {
        val added = app.delivery.send(session.id, photoIds)
        val text = when {
            added == 0 -> "${session.client} já tem ${if (count == 1) "essa foto. Para outra edição, use Versões" else "essas fotos"}"
            added == 1 -> "1 foto na fila de ${session.client}"
            else -> "$added fotos na fila de ${session.client}"
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (count == 1) "Entregar 1 foto" else "Entregar $count fotos", style = MaterialTheme.typography.titleLarge)
            if (settings?.deliveryEnabled == false) {
                Text("A entrega local está desligada. Ligue em Entrega local para o cliente baixar pelo Wi-Fi.", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
                Button(onClick = { onDismiss(); onOpenDelivery() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Wifi, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir entrega local")
                }
                return@Column
            }
            if (open.isNotEmpty()) {
                Text("Para quem", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
                open.forEach { s ->
                    val active = s.id == settings?.deliveryActiveSession
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { send(s) }.heightIn(min = 56.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (active) Icons.Outlined.CheckCircle else Icons.Outlined.Person, null, tint = if (active) Tone.Accent else Tone.TextMuted)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.client, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (s.event) "Galeria do evento · " else "") + "código ${s.code}" + if (active) " · cliente da vez" else "",
                                style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                            )
                        }
                    }
                }
            }
            Text("Cliente novo", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted, modifier = Modifier.padding(top = 6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Nome do cliente") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { scope.launch { send(app.delivery.newClient(name)) } },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Criar e entregar")
            }
            TextButton(onClick = { onDismiss(); onOpenDelivery() }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text("Abrir entrega local")
            }
        }
    }
}
