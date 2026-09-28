package com.example.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Tone

/** Picks the part of the event new frames belong to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentSheet(moments: List<String>, current: String, subfolder: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Momento do evento", style = MaterialTheme.typography.titleMedium)
            Text(
                if (subfolder) "As próximas fotos vão para a subpasta do momento." else "As próximas fotos recebem este momento para filtrar na galeria.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            (listOf("") + moments).forEachIndexed { i, moment ->
                val selected = moment == current
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onPick(moment) }.heightIn(min = 52.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (moment.isEmpty()) "Sem momento" else "%02d  %s".format(i, moment),
                        color = if (selected) Tone.Accent else Tone.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Text("Agora", style = MaterialTheme.typography.labelMedium, color = Tone.Accent)
                }
                HorizontalDivider(color = Tone.Line)
            }
        }
    }
}
