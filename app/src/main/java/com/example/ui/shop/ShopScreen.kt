package com.example.ui.shop

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.shop.ShopItem
import com.example.core.shop.ShopStatus
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

private const val SAVED = "\u0000salvos"

/**
 * The Loja tab: the presets from the photographer's API, shown by their finished look (the preview
 * inside each DNG). Filter by category or search, tap one to see it on your own photo and use it.
 */
@Composable
fun ShopScreen(app: TetherApp, onOpen: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val items by app.shop.items.collectAsState()
    val status by app.shop.status.collectAsState()
    val saved by app.shop.saved.collectAsState(initial = emptyMap())
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val grid = rememberLazyGridState()

    LaunchedEffect(Unit) {
        app.shop.loadSaved()
        app.shop.refreshIfStale()
    }

    val counts = remember(items) { items.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value } }
    val shown = remember(items, category, query, saved) {
        val q = query.trim().lowercase()
        items.filter { item ->
            when (category) {
                null -> true
                SAVED -> item.source in saved
                else -> item.category == category
            } && (q.isEmpty() || item.name.lowercase().contains(q) || item.categoryLabel.lowercase().contains(q) || item.category.lowercase().contains(q))
        }
    }
    // A new filter starts from the top, not in the middle of the old list.
    LaunchedEffect(category, query) { grid.scrollToItem(0) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = grid,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Loja de presets", style = MaterialTheme.typography.titleLarge, color = Tone.Text)
                        Text(
                            if (items.isEmpty()) "Presets grátis da sua API" else "${items.size} presets grátis · ${counts.size} categorias",
                            style = MaterialTheme.typography.bodySmall,
                            color = Tone.TextMuted,
                        )
                    }
                    if (status is ShopStatus.Loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), color = Tone.Info, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { scope.launch { app.shop.refresh() } }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar a lista", tint = Tone.Text)
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Buscar por nome ou estilo") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Tone.TextMuted) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Limpar busca") } }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Tone.Accent, unfocusedBorderColor = Tone.Line),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopChip(Icons.Outlined.Apps, "Todos", items.size, category == null) { category = null }
                ShopChip(Icons.Outlined.BookmarkAdded, "Salvos", saved.size, category == SAVED) { category = SAVED }
                counts.forEach { (c, n) -> ShopChip(null, com.example.core.shop.ShopCatalog.categoryLabel(c), n, category == c) { category = c } }
            }
        }
        (status as? ShopStatus.Offline)?.let { off ->
            item(key = "offline", span = { GridItemSpan(maxLineSpan) }) {
                Notice(Icons.Outlined.CloudOff, "${off.message}. Mostrando a lista salva no celular; os presets salvos funcionam normalmente.")
            }
        }
        if (items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyShop(status) { scope.launch { app.shop.refresh() } }
            }
        } else if (shown.isEmpty()) {
            item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (category == SAVED) "Nenhum preset da loja salvo ainda. Abra um e toque em Salvar."
                    else "Nada encontrado para \"$query\".",
                    color = Tone.TextMuted,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        }
        items(shown, key = { it.id }) { item ->
            ShopCard(app, item, saved = item.source in saved) { onOpen(item.id) }
        }
    }
}

@Composable
private fun ShopCard(app: TetherApp, item: ShopItem, saved: Boolean, onClick: () -> Unit) {
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        val b = app.shop.preview(item, 480)
        bitmap = b
        failed = b == null
    }
    Column(
        Modifier.clip(MaterialTheme.shapes.small).background(Tone.Surface).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Tone.SurfaceHigh), contentAlignment = Alignment.Center) {
            val b = bitmap
            when {
                b != null -> Image(b.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                failed -> Icon(Icons.Outlined.ImageNotSupported, contentDescription = "Prévia indisponível", tint = Tone.TextMuted)
                else -> CircularProgressIndicator(Modifier.size(20.dp), color = Tone.Info, strokeWidth = 2.dp)
            }
            if (saved) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(3.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Salvo em Meus presets", tint = Tone.Accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(item.name, style = MaterialTheme.typography.labelLarge, color = Tone.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // "Moody 690" already says its category; "Amber A1" doesn't.
            val sub = if (item.name.startsWith(item.categoryLabel)) "DNG + XMP" else item.categoryLabel
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun ShopChip(icon: ImageVector?, text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$text  $count", maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) } },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Tone.SurfaceHigh,
            labelColor = Tone.TextMuted,
            iconColor = Tone.TextMuted,
            selectedContainerColor = Tone.Accent,
            selectedLabelColor = Tone.OnAccent,
            selectedLeadingIconColor = Tone.OnAccent,
        ),
        border = null,
    )
}

@Composable
internal fun Notice(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Tone.SurfaceHigh).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Tone.Info, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Tone.Text, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun EmptyShop(status: ShopStatus, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (status) {
            is ShopStatus.Failed -> {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Tone.TextMuted, modifier = Modifier.size(40.dp))
                Text("Não deu para abrir a loja", style = MaterialTheme.typography.titleMedium, color = Tone.Text, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "${status.message}. A loja precisa de internet só para ver e pegar presets novos; os já salvos funcionam sem ela.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Tentar de novo") }
            }
            else -> {
                CircularProgressIndicator(color = Tone.Info)
                Text("Carregando a lista de presets…", color = Tone.TextMuted, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
