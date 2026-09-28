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

fun Settings.template(kind: MessageKind) = when (kind) {
    MessageKind.Preview -> msgPreview
    MessageKind.Delivered -> msgDelivered
    MessageKind.Payment -> msgPayment
    MessageKind.Selection -> msgSelection
}

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
        )
    }
    var kind by remember { mutableStateOf(initial) }
    var text by remember(kind) { mutableStateOf(MessageTemplates.fill(settings.template(kind), data)) }
    val attachOptions = buildList {
        add(null)
        if (settings.lightShareEnabled) add(ShareSet.LIGHT)
        if (photos.any { it.editedUri != null }) add(ShareSet.EDITED)
    }
    var attach by remember { mutableStateOf<ShareSet?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mensagem", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageKind.entries.forEach { k -> SheetChip(k.label, kind == k) { kind = k } }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
            )
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
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Abrir no WhatsApp") }
        }
    }
}

@Composable
fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Tone.SurfaceHigh, selectedLabelColor = Tone.Accent, labelColor = Tone.TextMuted,
        ),
    )
}
