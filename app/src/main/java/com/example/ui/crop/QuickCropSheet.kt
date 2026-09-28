package com.example.ui.crop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.core.render.CropGroup
import com.example.core.render.CropRatio
import com.example.ui.theme.Tone

/**
 * "Recorte rápido" for the selected photos: one tap gives each the widest centred crop of a format
 * (Instagram 4:5, Stories 9:16, 10 × 15…). [onOpenFull] opens the full crop tool when only one is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCropSheet(count: Int, onDismiss: () -> Unit, onOpenFull: (() -> Unit)?, onPick: (CropRatio?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Recorte rápido", style = MaterialTheme.typography.titleMedium)
            Text(
                "Centralizado em ${if (count == 1) "1 foto" else "$count fotos"}. Dá para ajustar cada uma depois em Recortar.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
            onOpenFull?.let { open ->
                OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text("Abrir o recorte completo") }
            }
            CropGroup.entries.forEach { group ->
                Text(group.label, style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted, modifier = Modifier.padding(top = 8.dp))
                CropRatio.entries.filter { it.group == group && it != CropRatio.FREE }.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onPick(r) }.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.label, color = Tone.Text, modifier = Modifier.weight(1f))
                        Text(r.detail, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, maxLines = 1)
                    }
                }
            }
            TextButton(onClick = { onPick(null) }) { Text("Remover recorte", color = Tone.Error) }
        }
    }
}
