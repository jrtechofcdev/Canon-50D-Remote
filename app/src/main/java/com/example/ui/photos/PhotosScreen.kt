package com.example.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.preset.Preset
import com.example.ui.PhotoActions
import com.example.ui.ShareSet
import com.example.ui.components.FormatTag
import com.example.ui.session.MomentSheet
import com.example.ui.crop.QuickCropSheet
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.outlined.EditNote

enum class PhotoFilter(val label: String) {
    All("Todas"), Session("Sessão atual"), Favorites("Favoritas"), ClientPicks("Escolhidas pelo cliente"),
}

fun List<Photo>.filtered(filter: PhotoFilter, minStars: Int, moment: String?, settings: Settings): List<Photo> = when (filter) {
    PhotoFilter.All -> this
    PhotoFilter.Session -> filter { it.session == settings.sessionKey }
    PhotoFilter.Favorites -> filter { it.favorite }
    PhotoFilter.ClientPicks -> filter { it.clientPick }
}.filter { (minStars == 0 || it.rating >= minStars) && (moment == null || it.moment == moment) }

/** A batch job's progress, shown in a dialog while it runs one photo at a time. */
private data class BatchProgress(val label: String, val done: Int, val total: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    app: TetherApp,
    actions: PhotoActions,
    onOpenPhoto: (Long) -> Unit,
    onSelectingChange: (Boolean) -> Unit = {},
    onCrop: (Long) -> Unit = {},
    onOpenDelivery: () -> Unit = {},
) {
    val all by remember { app.database.photoDao().observeAll() }.collectAsState(initial = emptyList())
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(PhotoFilter.All) }
    var minStars by rememberSaveable { mutableIntStateOf(0) }
    var moment by rememberSaveable { mutableStateOf<String?>(null) }
    val photos = all.filtered(filter, if (settings.ratingEnabled) minStars else 0, moment, settings)
        .filter { !settings.hideRejected || !it.rejected }

    val selected = remember { mutableStateListOf<Long>() }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(selecting) { onSelectingChange(selecting) }
    val selection = photos.filter { it.id in selected }
    var progress by remember { mutableStateOf<BatchProgress?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = selecting) { selected.clear() }

    fun runBatch(label: String, list: List<Photo>, step: suspend (Photo) -> Unit) = scope.launch {
        list.forEachIndexed { i, p ->
            progress = BatchProgress(label, i, list.size)
            runCatching { step(p) }
        }
        progress = null
        selected.clear()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected.clear() }) { Icon(Icons.Outlined.Close, contentDescription = "Cancelar seleção") }
                Text("${selected.size} selecionada(s)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    if (selected.size == photos.size) selected.clear() else { selected.clear(); selected.addAll(photos.map { it.id }) }
                }) { Icon(Icons.Outlined.SelectAll, contentDescription = "Selecionar tudo") }
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhotoFilter.entries.filter { it != PhotoFilter.ClientPicks || settings.clientPicksEnabled }.forEach {
                    GalleryChip(it.label, filter == it) { filter = it }
                }
                if (settings.ratingEnabled) {
                    (1..5).forEach { stars -> GalleryChip("★ $stars+", minStars == stars) { minStars = if (minStars == stars) 0 else stars } }
                }
            }
            val moments = all.mapNotNull { it.moment }.distinct()
            if (settings.momentsEnabled && moments.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GalleryChip("Todos os momentos", moment == null) { moment = null }
                    moments.forEach { m -> GalleryChip(m, moment == m) { moment = if (moment == m) null else m } }
                }
            }
        }

        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when (filter) {
                        PhotoFilter.Favorites -> "Nenhuma favorita ainda. Toque no coração ao ver uma foto."
                        PhotoFilter.ClientPicks -> "O cliente ainda não escolheu fotos. No modo cliente, ele toca no coração."
                        else -> "Nenhuma foto recebida. Conecte a 50D e dispare."
                    },
                    color = Tone.TextMuted,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(settings.galleryThumb.dp),
            // Room under the last row for the capture button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selecting) 16.dp else 88.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(photos, key = { it.id }) { photo ->
                val isSelected = photo.id in selected
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Tone.Surface)
                        .then(if (isSelected) Modifier.border(3.dp, Tone.Accent, MaterialTheme.shapes.extraSmall) else Modifier)
                        .combinedClickable(
                            onClick = {
                                when {
                                    !selecting -> onOpenPhoto(photo.id)
                                    isSelected -> selected.remove(photo.id)
                                    else -> selected.add(photo.id)
                                }
                            },
                            onLongClick = { if (!isSelected) selected.add(photo.id) },
                        ),
                ) {
                    AsyncImage(
                        model = photo.lookUri,
                        contentDescription = photo.savedBase,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FormatTag(photo.rawUri != null, photo.jpgUri != null, Modifier.align(Alignment.BottomStart).padding(4.dp))
                    Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (photo.blurry) TileIcon(Icons.Outlined.BlurOn, "Tremida", Tone.Error)
                        if (settings.eyesCheck && photo.eyesClosed > 0) TileIcon(Icons.Outlined.VisibilityOff, "Olhos fechados", Tone.Error)
                        if (settings.cullSuggestions && photo.suggested) TileIcon(Icons.Outlined.ThumbUp, "Sugerida", Tone.Accent)
                        if (photo.note != null || photo.audioNote != null) TileIcon(Icons.AutoMirrored.Outlined.StickyNote2, "Tem nota", Tone.Text)
                        if (photo.clientPick && settings.clientPicksEnabled) TileIcon(Icons.Filled.Favorite, "Escolhida pelo cliente", Tone.Accent)
                        else if (photo.favorite) TileIcon(Icons.Filled.Favorite, "Favorita", Tone.Text)
                    }
                    if (settings.ratingEnabled && photo.rating > 0) {
                        Text(
                            "★".repeat(photo.rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = Tone.Text,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle, contentDescription = "Selecionada", tint = Tone.Accent,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp),
                        )
                    }
                }
            }
        }

        if (selecting) {
            BatchBar(
                settings = settings,
                selection = selection,
                actions = actions,
                onPreset = { sheet = "preset" },
                onFavorite = {
                    val on = selection.any { !it.favorite }
                    scope.launch { app.database.photoDao().setFavorite(selection.map { it.id }, on); selected.clear() }
                },
                onMoment = { sheet = "moment" },
                onNote = { sheet = "note" },
                onMessage = { sheet = "message" },
                onWhatsApp = { sheet = "whatsapp" },
                onDeliver = { sheet = "deliver" },
                onCrop = { sheet = "crop" },
                onDelete = { sheet = "delete" },
                onShare = { set -> scope.launch { actions.share(selection, set, settings) } },
                onLightroom = { set -> scope.launch { actions.openInLightroom(selection, set, settings) } },
            )
        }
    }

    when (sheet) {
        "preset" -> BatchPresetSheet(presets, onDismiss = { sheet = null }) { preset ->
            sheet = null
            runBatch(if (preset == null) "Removendo preset" else "Aplicando ${preset.name}", selection) { app.looks.setLook(it, preset?.id, null) }
        }
        "moment" -> MomentSheet(settings.moments, current = "\u0000", subfolder = false, onPick = { m ->
            sheet = null
            scope.launch { app.database.photoDao().setMoment(selection.map { it.id }, m.ifEmpty { null }); selected.clear() }
        }, onDismiss = { sheet = null })
        "message" -> MessageSheet(actions, settings, selection, onDismiss = { sheet = null })
        "note" -> NoteSheet(app, selection, onDismiss = { sheet = null; selected.clear() })
        "crop" -> QuickCropSheet(
            count = selection.size,
            onDismiss = { sheet = null },
            onOpenFull = selection.singleOrNull()?.let { p -> { sheet = null; selected.clear(); onCrop(p.id) } },
        ) { ratio ->
            sheet = null
            runBatch(if (ratio == null) "Removendo recorte" else "Recortando ${ratio.label}", selection) { app.looks.quickCrop(it, ratio) }
        }
        "deliver" -> com.example.ui.delivery.DeliverSheet(
            app, selection.map { it.id },
            onDismiss = { sheet = null; selected.clear() },
            onOpenDelivery = onOpenDelivery,
        )
        "whatsapp" -> WhatsAppSheet(app, actions, settings, selection, onDismiss = { sheet = null; selected.clear() })
        "delete" -> AlertDialog(
            onDismissRequest = { sheet = null },
            containerColor = Tone.Surface,
            title = { Text("Apagar ${selection.size} foto(s) do celular?") },
            text = { Text("Os arquivos saem do celular (JPG, RAW, editadas e notas). Os originais continuam no cartão da câmera.") },
            confirmButton = {
                TextButton(onClick = {
                    sheet = null
                    runBatch("Apagando", selection) { p -> withContext(Dispatchers.IO) { deletePhoto(app, p) } }
                }) { Text("Apagar", color = Tone.Error) }
            },
            dismissButton = { TextButton(onClick = { sheet = null }) { Text("Cancelar") } },
        )
    }

    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Tone.Surface,
            title = { Text(p.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${p.done + 1} de ${p.total}", color = Tone.TextMuted)
                    LinearProgressIndicator(
                        progress = { (p.done + 1f) / p.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = Tone.Info,
                        trackColor = Tone.SurfaceHigh,
                    )
                }
            },
            confirmButton = {},
        )
    }
}

/** Removes every file the app made for [photo] (never the card's original) and its row. */
suspend fun deletePhoto(app: TetherApp, photo: Photo) {
    listOfNotNull(photo.jpgUri, photo.rawUri, photo.editedUri).forEach(app.store::delete)
    photo.previewPath?.let { java.io.File(it).delete() }
    photo.audioNote?.let { java.io.File(it).delete() }
    app.database.photoDao().delete(photo.id)
}

@Composable
private fun BatchBar(
    settings: Settings,
    selection: List<Photo>,
    actions: PhotoActions,
    onPreset: () -> Unit,
    onFavorite: () -> Unit,
    onMoment: () -> Unit,
    onNote: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onDeliver: () -> Unit,
    onCrop: () -> Unit,
    onDelete: () -> Unit,
    onShare: (ShareSet) -> Unit,
    onLightroom: (ShareSet) -> Unit,
) {
    // Offer only the file sets every selected photo can provide at least partly.
    val sets = selection.flatMap { actions.availableSets(it, settings) }.distinct()
    val lrSets = selection.flatMap { actions.availableSets(it, settings, includeLight = false) }.distinct()
    Row(
        Modifier.fillMaxWidth().background(Tone.Surface).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarAction(Icons.AutoMirrored.Outlined.Send, "WhatsApp", onWhatsApp)
        BarAction(Icons.Outlined.WifiTethering, "Entregar", onDeliver)
        BarAction(Icons.Outlined.Style, "Preset", onPreset)
        BarAction(Icons.Outlined.Crop, "Recorte", onCrop)
        BarMenu(Icons.Outlined.Share, "Enviar", sets, onShare)
        BarMenu(Icons.Outlined.PhotoLibrary, "Lightroom", lrSets, onLightroom)
        BarAction(Icons.AutoMirrored.Outlined.Chat, "Mensagem", onMessage)
        BarAction(if (selection.all { it.favorite }) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favoritar", onFavorite)
        if (settings.momentsEnabled) BarAction(Icons.Outlined.Flag, "Momento", onMoment)
        if (settings.notesEnabled) BarAction(Icons.Outlined.EditNote, "Nota", onNote)
        BarAction(Icons.Outlined.Delete, "Apagar", onDelete)
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 64.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Tone.Text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
    }
}

@Composable
private fun BarMenu(icon: ImageVector, label: String, sets: List<ShareSet>, onPick: (ShareSet) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarAction(icon, label) { if (sets.size == 1) onPick(sets.first()) else if (sets.isNotEmpty()) open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Tone.SurfaceHigh) {
            sets.forEach { set -> DropdownMenuItem(text = { Text(set.label) }, onClick = { open = false; onPick(set) }) }
        }
    }
}

@Composable
private fun TileIcon(icon: ImageVector, description: String, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
}

@Composable
private fun GalleryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Tone.SurfaceHigh,
            selectedLabelColor = Tone.Text,
            labelColor = Tone.TextMuted,
        ),
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BatchPresetSheet(presets: List<Preset>, onDismiss: () -> Unit, onPick: (Preset?) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            item { Text("Aplicar preset nas selecionadas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            item { TextButton(onClick = { onPick(null) }) { Text("Sem preset (voltar ao original)", color = Tone.TextMuted) } }
            items(presets.size) { i ->
                val p = presets[i]
                TextButton(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                    Text(p.name, color = Tone.Text, modifier = Modifier.weight(1f))
                    Text(p.group, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
                }
            }
        }
    }
}
