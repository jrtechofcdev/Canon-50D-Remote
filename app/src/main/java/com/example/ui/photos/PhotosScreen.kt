package com.example.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.ui.components.FormatTag
import com.example.ui.theme.Tone

enum class PhotoFilter(val label: String) { All("Todas"), Session("Sessão atual"), Favorites("Favoritas") }

fun List<Photo>.filtered(filter: PhotoFilter, minStars: Int, settings: Settings): List<Photo> = when (filter) {
    PhotoFilter.All -> this
    PhotoFilter.Session -> filter { it.session == settings.sessionKey }
    PhotoFilter.Favorites -> filter { it.favorite }
}.filter { minStars == 0 || it.rating >= minStars }

@Composable
fun PhotosScreen(app: TetherApp, onOpenPhoto: (Long) -> Unit) {
    val all by remember { app.database.photoDao().observeAll() }.collectAsState(initial = emptyList())
    val settings by app.settings.settings.collectAsState(initial = Settings())
    var filter by rememberSaveable { mutableStateOf(PhotoFilter.All) }
    var minStars by rememberSaveable { mutableIntStateOf(0) }
    val photos = all.filtered(filter, if (settings.ratingEnabled) minStars else 0, settings)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhotoFilter.entries.forEach {
                FilterChip(
                    selected = filter == it,
                    onClick = { filter = it },
                    label = { Text(it.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Tone.SurfaceHigh,
                        selectedLabelColor = Tone.Text,
                        labelColor = Tone.TextMuted,
                    ),
                )
            }
            if (settings.ratingEnabled) {
                (1..5).forEach { stars ->
                    FilterChip(
                        selected = minStars == stars,
                        onClick = { minStars = if (minStars == stars) 0 else stars },
                        label = { Text("★ $stars+") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Tone.SurfaceHigh,
                            selectedLabelColor = Tone.Text,
                            labelColor = Tone.TextMuted,
                        ),
                    )
                }
            }
        }

        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when (filter) {
                        PhotoFilter.Favorites -> "Nenhuma favorita ainda. Toque no coração ao ver uma foto."
                        else -> "Nenhuma foto recebida. Conecte a 50D e dispare."
                    },
                    color = Tone.TextMuted,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(photos, key = { it.id }) { photo ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Tone.Surface)
                        .clickable { onOpenPhoto(photo.id) },
                ) {
                    AsyncImage(
                        model = photo.lookUri,
                        contentDescription = photo.savedBase,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FormatTag(photo.rawUri != null, photo.jpgUri != null, Modifier.align(Alignment.BottomStart).padding(4.dp))
                    if (photo.favorite) {
                        Icon(
                            Icons.Filled.Favorite, contentDescription = "Favorita", tint = Tone.Text,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
                        )
                    }
                    if (settings.ratingEnabled && photo.rating > 0) {
                        Text(
                            "★".repeat(photo.rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = Tone.Text,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
