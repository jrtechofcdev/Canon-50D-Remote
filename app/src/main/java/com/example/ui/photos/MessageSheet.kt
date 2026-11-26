package com.example.ui.photos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.messages.MessageData
import com.example.core.messages.MessageKind
import com.example.core.messages.MessageTemplates
import com.example.ui.PhotoActions
import com.example.ui.ShareSet
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Search

/** The text an older, fixed message kind had in Ajustes (before the message library). */
fun Settings.legacyTemplate(kind: MessageKind) = when (kind) {
    MessageKind.WhatsApp -> msgWhatsApp
    MessageKind.Preview -> msgPreview
    MessageKind.Delivered -> msgDelivered
    MessageKind.Payment -> msgPayment
    MessageKind.Selection -> msgSelection
}

/** Every message the photographer keeps (Ajustes → Mensagens). */
fun Settings.messages(): List<com.example.core.messages.MessageTemplate> =
    com.example.core.messages.MessageLibrary.all(messageLibrary) { legacyTemplate(it) }

fun Settings.template(kind: MessageKind): String =
    messages().firstOrNull { it.id == com.example.core.messages.MessageLibrary.kindIds[kind] }?.text ?: legacyTemplate(kind)

/**
 * Ready-made WhatsApp message for [photos]: pick the template, adjust the text, optionally attach
 * the photos (light JPG with watermark, or the edited copies), send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSheet(
    actions: PhotoActions,
    settings: Settings,
    photos: List<Photo>,
    initial: MessageKind = MessageKind.Preview,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val data = remember(photos) {
        MessageData(
            client = photos.firstOrNull()?.session?.takeIf { it != "Geral" }.orEmpty(),
            count = photos.size,
            date = photos.minOfOrNull { it.capturedAt } ?: System.currentTimeMillis(),
            names = photos.map { it.savedBase },
            pix = settings.pixKey,
        )
    }
    val library = remember(settings) { settings.messages() }
    var templateId by remember { mutableStateOf(com.example.core.messages.MessageLibrary.kindIds[initial]) }
    val filled = remember(templateId, data) { MessageTemplates.fill(library.firstOrNull { it.id == templateId }?.text.orEmpty(), data) }
    // Local, horário, valor…: typed here; the text follows them until it is edited by hand.
    val extra = remember(templateId) { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var typed by remember(templateId) { mutableStateOf<String?>(null) }
    val text = MessageTemplates.applyExtra(typed ?: filled, extra)
    val left = MessageTemplates.missing(text)
    val attachOptions = buildList {
        add(null)
        if (settings.lightShareEnabled) add(ShareSet.LIGHT)
        if (photos.any { it.editedUri != null }) add(ShareSet.EDITED)
    }
    var attach by remember { mutableStateOf<ShareSet?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mensagem", style = MaterialTheme.typography.titleMedium)
            TemplatePicker(library, templateId) { templateId = it?.id }
            FieldsToFill(MessageTemplates.missing(filled), extra)
            OutlinedTextField(
                value = text,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
            )
            MissingNote(left)
            if (photos.isNotEmpty() && attachOptions.size > 1) {
                Text("Enviar junto", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachOptions.forEach { set ->
                        SheetChip(set?.let { "${it.label} (${photos.size})" } ?: "Só o texto", attach == set) { attach = set }
                    }
                }
            }
            Button(
                onClick = { scope.launch { actions.sendMessage(text, photos, attach, settings); onDismiss() } },
                enabled = text.isNotBlank() && left.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Abrir no WhatsApp") }
        }
    }
}

/**
 * Pick a message by its short code: type "ent" and "Fotos entregues" comes up; tap it and the full
 * text (from Ajustes → Mensagens, with the client's name and date) goes into the message.
 */
@Composable
fun TemplatePicker(
    library: List<com.example.core.messages.MessageTemplate>,
    selectedId: String?,
    allowNone: Boolean = false,
    onPick: (com.example.core.messages.MessageTemplate?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<com.example.core.messages.MessageLibrary.Category?>(null) }
    val found = remember(library, query, category) {
        com.example.core.messages.MessageLibrary.search(library, query).filter { category == null || com.example.core.messages.MessageLibrary.categoryOf(it) == category }
    }
    val present = remember(library) { library.map { com.example.core.messages.MessageLibrary.categoryOf(it) }.toSet() }
    OutlinedTextField(
        value = query,
        onValueChange = { q ->
            query = q.take(20)
            // An exact code picks its message straight away.
            library.firstOrNull { it.code.equals(q.trim(), ignoreCase = true) }?.let(onPick)
        },
        label = { Text("Atalho da mensagem") },
        placeholder = { Text("Ex.: fotos, ent, pix, agenda") },
        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    // Steps of the job, to narrow 35 messages down to a handful.
    if (present.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetChip("Todas", category == null) { category = null }
        com.example.core.messages.MessageLibrary.Category.entries.filter { it in present }.forEach { c -> SheetChip(c.label, category == c) { category = c } }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNone) SheetChip("Sem texto", selectedId == null) { onPick(null) }
        found.forEach { t -> SheetChip("${t.name} · ${t.code}", t.id == selectedId) { onPick(t) } }
        if (found.isEmpty()) Text("Nenhuma mensagem com \"$query\". Crie em Ajustes → Mensagens.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
    }
}

/**
 * What a message needs and the app doesn't know (local, horário, serviço, valor, prazo, and an empty
 * Pix key or Wi-Fi): one field each, so no {campo} reaches the client.
 */
@Composable
fun FieldsToFill(keys: List<String>, values: MutableMap<String, String>) {
    if (keys.isEmpty()) return
    Text("Preencher antes de enviar", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
    keys.forEach { key ->
        OutlinedTextField(
            value = values[key].orEmpty(),
            onValueChange = { values[key] = it.take(200) },
            label = { Text(MessageTemplates.FIELDS[key] ?: key) },
            singleLine = key != "lista",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Says which fields are still empty; the send button waits for them (or for the line to be removed). */
@Composable
fun MissingNote(left: List<String>) {
    if (left.isEmpty()) return
    Text(
        "Falta preencher: " + left.joinToString(", ") { MessageTemplates.FIELDS[it] ?: it } + ". Preencha acima ou apague a linha no texto.",
        style = MaterialTheme.typography.bodySmall, color = Tone.Warning,
    )
}

@Composable
fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    com.example.ui.components.JrChip(label, selected, onClick)
}
