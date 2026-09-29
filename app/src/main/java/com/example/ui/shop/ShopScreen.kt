package com.example.ui.shop

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.shop.PresetShop
import com.example.core.shop.ShopCatalog
import com.example.core.shop.ShopItem
import com.example.core.shop.ShopStatus
import com.example.ui.theme.Tone
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val SAVED = "\u0000salvos"

/** How far below the screen the previews are fetched ahead, in cards. */
private const val AHEAD = 12

/**
 * The Loja tab: the presets from the photographer's API, shown by their finished look (the preview
 * inside each DNG). Filter by category or search, tap one to see it big, or hold to select several
 * and save or download them together.
 */
@Composable
fun ShopScreen(app: TetherApp, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val items by app.shop.items.collectAsState()
    val status by app.shop.status.collectAsState()
    val saved by app.shop.saved.collectAsState(initial = emptyMap())
    val settings by app.settings.settings.collectAsState(initial = Settings())
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var progress by remember { mutableStateOf<Pair<String, Float>?>(null) }
    val grid = rememberLazyGridState()
    val selecting = selected.isNotEmpty()

    LaunchedEffect(Unit) {
        app.shop.loadSaved()
        app.shop.refreshIfStale()
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

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

    // The next cards below the screen load while the current ones are looked at.
    val latestShown by rememberUpdatedState(shown)
    LaunchedEffect(Unit) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }.lastOrNull() }
            .distinctUntilChanged()
            .collect { lastId ->
                val list = latestShown
                val from = if (lastId == null) 0 else list.indexOfFirst { it.id == lastId } + 1
                if (from in 0 until list.size) app.shop.prefetch(list.subList(from, minOf(list.size, from + AHEAD)))
            }
    }

    fun chosen() = items.filter { it.id in selected }

    fun runBatch(label: String, work: suspend (List<ShopItem>, (Int) -> Unit) -> Int, done: (Int, Int) -> String) {
        val list = chosen()
        if (list.isEmpty() || progress != null) return
        progress = label to 0f
        scope.launch {
            val ok = work(list) { n -> progress = label to n / list.size.toFloat() }
            progress = null
            selected = emptySet()
            Toast.makeText(context, done(ok, list.size), Toast.LENGTH_LONG).show()
        }
    }

    val saveSelected = {
        runBatch("Salvando", { list, step -> app.shop.saveAll(list, step) }) { ok, total ->
            if (ok == total) "$ok presets salvos em Meus presets" else "$ok de $total salvos. Os outros não abriram; tente de novo com internet."
        }
    }
    val downloadSelected = {
        runBatch("Baixando", { list, step -> app.shop.downloadAll(list, step) }) { ok, total ->
            "$ok de $total baixados em ${PresetShop.ROOT}"
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            app.store.takePersistablePermission(uri)
            scope.launch {
                app.settings.setOrClear(SettingKeys.presetsFolder, uri.toString())
                app.presets.attachFolder()
                downloadSelected()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = grid,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selecting) 120.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                if (selecting) {
                    SelectionHeader(
                        count = selected.size,
                        allShown = shown.isNotEmpty() && shown.all { it.id in selected },
                        onClose = { selected = emptySet() },
                        onAll = { selected = if (shown.all { it.id in selected }) selected - shown.map { it.id }.toSet() else selected + shown.map { it.id } },
                    )
                } else {
                    ShopHeader(
                        total = items.size,
                        categories = counts.size,
                        loading = status is ShopStatus.Loading,
                        query = query,
                        onQuery = { query = it },
                        onRefresh = { scope.launch { app.shop.refresh() } },
                    )
                }
            }
            item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShopChip(Icons.Outlined.Apps, "Todos", items.size, category == null) { category = null }
                    ShopChip(Icons.Outlined.BookmarkAdded, "Salvos", saved.size, category == SAVED) { category = SAVED }
                    counts.forEach { (c, n) -> ShopChip(null, ShopCatalog.categoryLabel(c), n, category == c) { category = c } }
                }
            }
            if (!selecting && items.isNotEmpty()) {
                item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Segure um preset para marcar vários e salvar ou baixar de uma vez.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
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
                ShopCard(
                    app = app,
                    item = item,
                    saved = item.source in saved,
                    selecting = selecting,
                    selected = item.id in selected,
                    onClick = {
                        if (selecting) selected = if (item.id in selected) selected - item.id else selected + item.id
                        else onOpen(item.id)
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selected = selected + item.id
                    },
                )
            }
        }

        if (selecting) {
            SelectionBar(
                count = selected.size,
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter),
                onSave = saveSelected,
                onDownload = { if (settings.presetsFolder == null) pickFolder.launch(null) else downloadSelected() },
            )
        }
    }
}

@Composable
private fun ShopHeader(total: Int, categories: Int, loading: Boolean, query: String, onQuery: (String) -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Loja de presets", style = MaterialTheme.typography.titleLarge, color = Tone.Text)
                Text(
                    if (total == 0) "Presets grátis da sua API" else "$total presets grátis · $categories categorias",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.TextMuted,
                )
            }
            if (loading) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), color = Tone.Info, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar a lista", tint = Tone.Text) }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Buscar por nome ou estilo") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Tone.TextMuted) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Limpar busca") } }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Tone.Accent, unfocusedBorderColor = Tone.Line),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

@Composable
private fun SelectionHeader(count: Int, allShown: Boolean, onClose: () -> Unit, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cancelar seleção", tint = Tone.Text) }
        Text(
            if (count == 1) "1 selecionado" else "$count selecionados",
            style = MaterialTheme.typography.titleLarge,
            color = Tone.Text,
            modifier = Modifier.weight(1f),
        )
        TextButton(onAll) {
            Icon(Icons.Outlined.SelectAll, contentDescription = null, tint = Tone.Text, modifier = Modifier.size(18.dp))
            Text(if (allShown) "  Desmarcar" else "  Todos da lista", color = Tone.Text)
        }
    }
}

@Composable
private fun TextButton(onClick: () -> Unit, content: @Composable () -> Unit) =
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { content() }

@Composable
private fun SelectionBar(count: Int, progress: Pair<String, Float>?, modifier: Modifier, onSave: () -> Unit, onDownload: () -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Tone.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (progress != null) {
            Text("${progress.first}… ${(progress.second * count).toInt()} de $count", style = MaterialTheme.typography.bodySmall, color = Tone.Info)
            LinearProgressIndicator(progress = { progress.second }, modifier = Modifier.fillMaxWidth(), color = Tone.Info, trackColor = Tone.SurfaceHigh)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDownload, enabled = progress == null, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = null, tint = Tone.Text, modifier = Modifier.size(18.dp))
                Text("  Baixar $count", color = Tone.Text, maxLines = 1)
            }
            Button(
                onClick = onSave,
                enabled = progress == null,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Salvar $count", maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopCard(
    app: TetherApp,
    item: ShopItem,
    saved: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Already in memory (scrolling back): shown in the first frame, with no fade.
    val cached = remember(item.id) { app.shop.cachedPreview(item) }
    var bitmap by remember(item.id) { mutableStateOf(cached) }
    var failed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        if (bitmap != null) return@LaunchedEffect
        val b = app.shop.preview(item, PresetShop.GRID_EDGE)
        bitmap = b
        failed = b == null
    }
    // A short fade when a picture arrives from the network, so the grid fills in without popping.
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(if (cached != null) 0 else 160), label = "fade")
    val shape = MaterialTheme.shapes.small
    Column(
        Modifier.clip(shape).background(Tone.Surface)
            .then(if (selected) Modifier.border(3.dp, Tone.Accent, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Tone.SurfaceHigh), contentAlignment = Alignment.Center) {
            val b = bitmap
            when {
                b != null -> Image(
                    b.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                )
                failed -> Icon(Icons.Outlined.ImageNotSupported, contentDescription = "Prévia indisponível", tint = Tone.TextMuted)
            }
            if (selecting) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp).clip(CircleShape)
                        .background(if (selected) Tone.Accent else Color.Black.copy(alpha = 0.45f))
                        .border(2.dp, if (selected) Tone.Accent else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selecionado", tint = Tone.OnAccent, modifier = Modifier.size(24.dp))
                }
            }
            if (saved) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(3.dp),
                ) {
                    Icon(Icons.Outlined.BookmarkAdded, contentDescription = "Salvo em Meus presets", tint = Tone.Accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(item.name, style = MaterialTheme.typography.labelLarge, color = Tone.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // "Moody 690" already says its category; "Amber A1" doesn't.
            val sub = if (item.name.startsWith(item.categoryLabel)) "DNG e XMP" else item.categoryLabel
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
        modifier = Modifier.heightIn(min = 40.dp),
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
                    modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp),
                ) { Text("Tentar de novo") }
            }
            else -> {
                CircularProgressIndicator(color = Tone.Info)
                Text("Carregando a lista de presets…", color = Tone.TextMuted, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
