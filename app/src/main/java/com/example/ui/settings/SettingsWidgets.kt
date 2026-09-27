package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.components.SectionLabel
import com.example.ui.theme.ReadoutStyle
import com.example.ui.theme.Tone
import kotlin.math.roundToInt

/** A titled block of related options. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    SectionLabel(title)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
fun ToggleRow(title: String, detail: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Tone.Text else Tone.TextMuted)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Tone.Accent, checkedThumbColor = Tone.OnAccent),
        )
    }
}

/**
 * Numeric option. The value is only written when the finger lifts, so dragging doesn't hammer DataStore.
 */
@Composable
fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    enabled: Boolean = true,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = (dragging / step).roundToInt() * step
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Tone.Text else Tone.TextMuted, modifier = Modifier.weight(1f))
            Text(format(shown), style = ReadoutStyle.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize), color = Tone.Text)
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(shown.coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Tone.Accent,
                activeTrackColor = Tone.TextMuted,
                inactiveTrackColor = Tone.SurfaceHigh,
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

/** One choice out of a few, shown as chips that wrap on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Tone.Text else Tone.TextMuted)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onPick(value) },
                    enabled = enabled,
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Tone.SurfaceHigh,
                        selectedLabelColor = Tone.Accent,
                        labelColor = Tone.TextMuted,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = enabled,
                        selected = isSelected,
                        borderColor = Tone.Line,
                        selectedBorderColor = Tone.Accent,
                        selectedBorderWidth = 1.dp,
                    ),
                )
            }
        }
    }
}

@Composable
fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.padding(vertical = 8.dp))
}
