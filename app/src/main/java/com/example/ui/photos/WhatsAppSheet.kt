package com.example.ui.photos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.messages.Contact
import com.example.core.messages.MessageData
import com.example.core.messages.MessageKind
import com.example.core.messages.MessageTemplates
import com.example.core.messages.WhatsAppNumber
import com.example.ui.PhotoActions
import com.example.ui.ShareSet
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * Sends [photos] straight to a client's WhatsApp: type the number (or pick a recent one) and the chat
 * opens with the photos already attached. [forClient] is the client-mode version: just the number,
 * and no list of other clients' numbers on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppSheet(
    app: TetherApp,
    actions: PhotoActions,
    settings: Settings,
    photos: List<Photo>,
    forClient: Boolean = false,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val recent = remember(settings.recentContacts) { WhatsAppNumber.decode(settings.recentContacts) }
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val normalized = WhatsAppNumber.normalize(number)

    val sets = buildList {
        if (settings.lightShareEnabled) add(ShareSet.LIGHT)
        if (photos.any { it.editedUri != null }) add(ShareSet.EDITED)
        if (photos.any { it.jpgUri != null }) add(ShareSet.JPG)
    }.ifEmpty { listOf(ShareSet.JPG) }
    var set by remember { mutableStateOf(sets.first()) }

    val data = MessageData(
        client = name.ifBlank { photos.firstOrNull()?.session?.takeIf { it != "Geral" }.orEmpty() },
        count = photos.size,
        date = photos.minOfOrNull { it.capturedAt } ?: System.currentTimeMillis(),
        names = photos.map { it.savedBase },
    )
    var kind by remember { mutableStateOf<MessageKind?>(if (forClient) null else MessageKind.Delivered) }
    var text by remember(kind) { mutableStateOf(kind?.let { MessageTemplates.fill(settings.template(it), data) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (forClient) "Receber no seu WhatsApp" else "Enviar no WhatsApp",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${photos.size} foto(s). A conversa abre com as fotos já anexadas; é só tocar em enviar.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.filter { c -> c.isDigit() || c in "+()- " }.take(22); error = null },
                label = { Text(if (forClient) "Seu número com DDD" else "Número do cliente com DDD") },
                placeholder = { Text("(11) 99999-9999") },
                supportingText = {
                    when {
                        error != null -> Text(error!!, color = Tone.Error)
                        normalized != null -> Text(WhatsAppNumber.pretty(normalized), color = Tone.Accent)
                        else -> Text("Sem o +55, o app coloca sozinho.")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!forClient) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nome do cliente (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recent.isNotEmpty()) {
                    Text("Recentes", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { c ->
                            SheetChip(c.name.ifBlank { WhatsAppNumber.pretty(c.number) }, WhatsAppNumber.normalize(number) == c.number) {
                                number = WhatsAppNumber.pretty(c.number)
                                name = c.name
                            }
                        }
                    }
                }
                if (sets.size > 1) {
                    Text("O que vai", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sets.forEach { s -> SheetChip(label(s), set == s) { set = s } }
                    }
                }
                Text("Mensagem junto", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetChip("Sem texto", kind == null) { kind = null }
                    MessageKind.entries.forEach { k -> SheetChip(k.label, kind == k) { kind = k } }
                }
                if (kind != null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                    )
                }
            }
            Button(
                onClick = {
                    val digits = normalized
                    if (digits == null) {
                        error = "Confira o número: DDD + número, ex.: (11) 99999-9999"
                        return@Button
                    }
                    scope.launch {
                        val list = WhatsAppNumber.remember(recent, Contact(digits, name.trim()))
                        app.settings.set(SettingKeys.recentContacts, WhatsAppNumber.encode(list))
                        actions.sendToWhatsApp(digits, photos, if (forClient) sets.first() else set, if (forClient) "" else text, settings)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text(normalized?.let { "Abrir conversa com ${WhatsAppNumber.pretty(it)}" } ?: "Abrir conversa no WhatsApp") }
        }
    }
}

private fun label(set: ShareSet) = when (set) {
    ShareSet.LIGHT -> "Pronta (preset + marca d'água)"
    ShareSet.EDITED -> "Editada, tamanho cheio"
    ShareSet.JPG -> "JPG original"
    else -> set.label
}
