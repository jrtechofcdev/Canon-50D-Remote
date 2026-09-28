package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.TileShape
import com.example.ui.theme.Tone
import com.example.ui.theme.WordmarkStyle

/** The Jr_Tech_OFC symbol in its brand gradient. */
@Composable
fun BrandSymbol(size: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(R.drawable.ic_brand_symbol),
        contentDescription = null,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.size(size),
    )
}

/** App name set like the brand's wordmark, with the symbol beside it. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandSymbol(28.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("JR C50D Sync", style = WordmarkStyle, color = Tone.Text)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted) }
        }
    }
}

/**
 * State marker in the shape of one of the logo's blocks: mint when live, red on failure, grey otherwise.
 * It only ever marks a real connection state.
 */
@Composable
fun StateTile(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 14.dp, height = 10.dp).clip(TileShape()).background(color))
}
