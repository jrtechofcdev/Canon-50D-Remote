package com.example.ui.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.delivery.LocalDelivery
import com.example.ui.theme.Tone

/**
 * The photos auto mode left out (shaken, out of focus, eyes closed): said once, with a way to send
 * them anyway or to dismiss the note.
 */
@Composable
fun SkippedCard(tool: LocalDelivery, onSendAnyway: (List<Long>) -> Unit) {
    val skipped by tool.skipped.collectAsState()
    if (skipped.isEmpty()) return
    DeliveryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BlurOn, null, tint = Tone.TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (skipped.size == 1) "1 foto ficou de fora do automático" else "${skipped.size} fotos ficaram de fora do automático",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            skipped.take(4).joinToString("\n") { "${it.name}: ${it.reason}" } + if (skipped.size > 4) "\n…" else "",
            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSendAnyway(skipped.map { it.photoId }); tool.clearSkipped() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Mandar mesmo assim")
            }
            TextButton(onClick = { tool.clearSkipped() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ok") }
        }
    }
}
