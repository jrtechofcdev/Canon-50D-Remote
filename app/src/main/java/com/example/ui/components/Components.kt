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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

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
        // Long names ("Fluorescente", "Personalizado") get a smaller size instead of being cut, and with big
        // system text whatever still doesn't fit ends in "…".
        Text(
            value,
            style = if (value.length > 8) ReadoutStyle.copy(fontSize = ReadoutStyle.fontSize * 0.72f) else ReadoutStyle,
            color = if (enabled) Tone.Text else Tone.TextMuted,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
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
            SheetHeader(title)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(options.distinctBy { EosValues.label(prop, it) }) { value ->
                    val selected = value == current
                    // The current value marked like every choice in the app: raised, with the mint block.
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(if (selected) Tone.SurfaceHigh else Tone.Background)
                            .clickable { onPick(value) }
                            .heightIn(min = 48.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            StatusMark(Tone.Accent, height = 12.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            EosValues.label(prop, value),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = if (selected) Tone.Text else Tone.TextMuted,
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
    // The brand block before the text, on the same veil as every caption over a photo.
    Row(
        modifier.clip(MaterialTheme.shapes.extraSmall).background(Tone.Scrim).padding(start = 5.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusMark(Tone.OnPhoto, height = 8.dp)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Tone.OnPhoto)
    }
}
