package com.example.ui.photos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
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
 * Sends [photos] to a client's WhatsApp in two taps. WhatsApp only goes straight to a chat that
 * already exists, so: 1) the client's chat opens with the automatic message typed; 2) back here, the
 * photos go to that same chat. [forClient] is the client-mode version: name and number only, and no
 * list of other clients on screen.
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
    var set by remember { mutableStateOf(sets.firstOrNull { it.name == settings.whatsDefaultSet } ?: sets.first()) }

    val data = MessageData(
        client = name.ifBlank { photos.firstOrNull()?.session?.takeIf { it != "Geral" }.orEmpty() },
        count = photos.size,
        date = photos.minOfOrNull { it.capturedAt } ?: System.currentTimeMillis(),
        names = photos.map { it.savedBase },
        pix = settings.pixKey,
    )
    val library = remember(settings) { settings.messages() }
    var templateId by remember { mutableStateOf(if (settings.whatsAutoMessage) com.example.core.messages.MessageLibrary.kindIds[MessageKind.WhatsApp] else null) }
    var edited by remember(templateId) { mutableStateOf<String?>(null) }
    val text = edited ?: library.firstOrNull { it.id == templateId }?.let { MessageTemplates.fill(it.text, data) }.orEmpty()
    var numberError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf(false) }
    // Which step is next: the chat first, then the photos.
    var chatOpened by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    fun valid(): String? {
        val digits = normalized
        if (settings.whatsRequireName && name.isBlank()) nameError = true
        if (digits == null) numberError = "Confira o número: DDD + número, ex.: (11) 99999-9999"
        return digits?.takeIf { !nameError }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = Tone.Accent)
                Spacer(Modifier.width(10.dp))
                Text(if (forClient) "Receber no seu WhatsApp" else "Enviar no WhatsApp", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${photos.size} foto(s). Primeiro abre a conversa, depois vão as fotos.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40); nameError = false },
                label = { Text(if (forClient) "Seu nome" else "Nome do cliente") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                isError = nameError,
                supportingText = if (nameError) ({ Text("Coloque o nome", color = Tone.Error) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.filter { c -> c.isDigit() || c in "+()- " }.take(22); numberError = null; chatOpened = false },
                label = { Text(if (forClient) "Seu WhatsApp com DDD" else "WhatsApp do cliente com DDD") },
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                placeholder = { Text("(11) 99999-9999") },
                supportingText = {
                    when {
                        numberError != null -> Text(numberError!!, color = Tone.Error)
                        normalized != null -> Text(WhatsAppNumber.pretty(normalized), color = Tone.Accent)
                        else -> Text("Sem o +55, o app coloca sozinho.")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!forClient) {
                if (recent.isNotEmpty()) {
                    SheetLabel(Icons.Outlined.History, "Recentes")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { c ->
                            SheetChip(c.name.ifBlank { WhatsAppNumber.pretty(c.number) }, WhatsAppNumber.normalize(number) == c.number) {
                                number = WhatsAppNumber.pretty(c.number)
                                name = c.name
                                nameError = false
                                chatOpened = false
                            }
                        }
                    }
                }
                if (sets.size > 1) {
                    SheetLabel(Icons.Outlined.Image, "O que vai")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sets.forEach { s -> SheetChip(label(s), set == s) { set = s } }
                    }
                }
            }
            // The message: typed by its short code, shown in full before it goes.
            SheetLabel(Icons.AutoMirrored.Outlined.Chat, "Mensagem")
            TemplatePicker(library, templateId, allowNone = true) { templateId = it?.id }
            if (templateId != null) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { edited = it },
                    label = { Text("Vai assim (dá para ajustar)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                )
            }

            StepButton(
                step = 1,
                icon = Icons.AutoMirrored.Outlined.Chat,
                text = normalized?.let { "Abrir conversa com ${WhatsAppNumber.pretty(it)}" } ?: "Abrir conversa",
                primary = !chatOpened,
                done = chatOpened,
            ) {
                val digits = valid() ?: return@StepButton
                scope.launch {
                    val list = WhatsAppNumber.remember(recent, Contact(digits, name.trim()))
                    app.settings.set(SettingKeys.recentContacts, WhatsAppNumber.encode(list))
                }
                if (actions.openWhatsAppChat(digits, text)) chatOpened = true
            }
            StepButton(
                step = 2,
                icon = Icons.Outlined.Image,
                text = if (sending) "Preparando as fotos…" else "Enviar ${photos.size} foto(s) nessa conversa",
                primary = chatOpened,
                done = false,
                enabled = !sending,
            ) {
                val digits = valid() ?: return@StepButton
                sending = true
                scope.launch {
                    val ok = actions.sendPhotosToWhatsApp(digits, photos, if (forClient) sets.first() else set, settings)
                    sending = false
                    if (ok) onDismiss()
                }
            }
            Text(
                if (chatOpened) "Mandou a mensagem? Agora toque em 2 e escolha enviar."
                else "Na primeira vez com um número, o WhatsApp só vai direto para a conversa depois que ela existe. Por isso a mensagem vai antes.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
        }
    }
}

@Composable
private fun SheetLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Tone.TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
    }
}

@Composable
private fun StepButton(step: Int, icon: ImageVector, text: String, primary: Boolean, done: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(if (done) Icons.Outlined.CheckCircle else icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("$step  ·  $text", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            content = content,
        )
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), content = content)
    }
}

private fun label(set: ShareSet) = when (set) {
    ShareSet.LIGHT -> "Pronta (preset + marca d'água)"
    ShareSet.EDITED -> "Editada, tamanho cheio"
    ShareSet.JPG -> "JPG original"
    else -> set.label
}
