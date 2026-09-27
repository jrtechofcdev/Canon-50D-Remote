package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.ptp.EosValues
import com.example.ui.theme.ReadoutStyle
import com.example.ui.theme.TileShape
import com.example.ui.theme.Tone

/** Section title used across screens. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Tone.TextMuted,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/** One cell of the exposure readout (label above, value below), tappable when the camera allows changing it. */
@Composable
fun Readout(label: String, value: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
        Text(value, style = ReadoutStyle, color = if (enabled) Tone.Text else Tone.TextMuted, maxLines = 1)
    }
}

/** Grid of values the camera accepts for one property. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValuePickerSheet(
    title: String,
    prop: Int,
    options: List<Int>,
    current: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(options.distinctBy { EosValues.label(prop, it) }) { value ->
                    val selected = value == current
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(if (selected) Tone.Accent else Tone.SurfaceHigh)
                            .clickable { onPick(value) }
                            .heightIn(min = 48.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            EosValues.label(prop, value),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Tone.OnAccent else Tone.Text,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Small format tag on thumbnails: which files of the frame are on the phone. */
@Composable
fun FormatTag(hasRaw: Boolean, hasJpg: Boolean, modifier: Modifier = Modifier) {
    val text = when {
        hasRaw && hasJpg -> "RAW+JPG"
        hasRaw -> "RAW"
        else -> "JPG"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Tone.Text,
        modifier = modifier
            .clip(TileShape(slant = 0.3f, corner = 0.15f))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
