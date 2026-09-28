package com.example.ui.preset

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.preset.Preset
import com.example.core.preset.forPicking
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/** Filter value for "favourites only". */
const val FAVORITES = "\\u0000fav"

/**
 * Presets as small pictures of the current photo with each look, so the choice is made by eye.
 * Favourites (star) come first, then the most recently used. Tap applies; hold pins or unpins as
 * favourite. The folder chips on top filter by category.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetPicker(
    app: TetherApp,
    presets: List<Preset>,
    selectedId: Long?,
    sourceUri: String?,
    onPick: (Preset?) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    showCategories: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val categories = remember(presets) { presets.map { it.group }.distinct() }
    val hasFavorites = presets.any { it.favorite }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = presets.filter {
        when (category) {
            null -> true
            FAVORITES -> it.favorite
            else -> it.group == category
        }
    }.forPicking()
    val label = if (onDark) Color.White else Tone.Text
    val muted = if (onDark) Color.White.copy(alpha = 0.6f) else Tone.TextMuted

    Column(modifier) {
        if (showCategories && (categories.size > 1 || hasFavorites)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryChip("Todos", null, category == null, onDark) { category = null }
                if (hasFavorites) CategoryChip("Favoritos", Icons.Filled.Star, category == FAVORITES, onDark) { category = FAVORITES }
                categories.forEach { c -> CategoryChip(c, Icons.Outlined.Folder, category == c, onDark) { category = c } }
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "none") {
                Tile(
                    name = "Original",
                    selected = selectedId == null,
                    favorite = false,
                    label = label,
                    onClick = { onPick(null) },
                    onLongClick = null,
                ) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Block, contentDescription = null, tint = muted)
                    }
                }
            }
            items(shown, key = { it.id }) { p ->
                Tile(
                    name = p.name,
                    selected = p.id == selectedId,
                    favorite = p.favorite,
                    label = label,
                    onClick = { onPick(p) },
                    onLongClick = { scope.launch { app.presets.setFavorite(p.id, !p.favorite) } },
                ) { Thumb(app, p, sourceUri) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    name: String,
    selected: Boolean,
    favorite: Boolean,
    label: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    picture: @Composable () -> Unit,
) {
    Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.small)
                .then(if (selected) Modifier.border(2.dp, Tone.Accent, MaterialTheme.shapes.small) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            picture()
            if (favorite) {
                Icon(
                    Icons.Filled.Star, contentDescription = "Favorito", tint = Tone.Accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(1.dp),
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Tone.Accent else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Thumb(app: TetherApp, preset: Preset, sourceUri: String?) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(sourceUri, preset.settings) {
        bitmap = sourceUri?.let { runCatching { app.looks.thumb(it, preset.settings) }.getOrNull() }
    }
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f))) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = preset.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun CategoryChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, onDark: Boolean, onClick: () -> Unit) {
    val bg = when {
        selected -> Tone.Accent
        onDark -> Color.White.copy(alpha = 0.12f)
        else -> Tone.SurfaceHigh
    }
    val fg = when {
        selected -> Tone.OnAccent
        onDark -> Color.White
        else -> Tone.Text
    }
    Row(
        Modifier.clip(CircleShape).background(bg).combinedClickableCompat(onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = combinedClickable(onClick = onClick)
