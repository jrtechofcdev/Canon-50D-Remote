package com.example.ui.shop

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.preset.Preset
import com.example.core.shop.ShopDownload
import com.example.core.shop.ShopItem
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * One preset of the Loja: its look (the DNG preview, or rendered on one of your photos), and the
 * three things to do with it: use it on a photo, save it to Meus presets, download the DNG and XMP.
 */
@Composable
fun ShopDetailScreen(
    app: TetherApp,
    itemId: Long,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onEditPhoto: (Long) -> Unit,
) {
    val items by app.shop.items.collectAsState()
    // Opened straight (after Android closed the app in the background): the list may not be loaded yet.
    LaunchedEffect(Unit) {
        app.shop.loadSaved()
        if (app.shop.items.value.none { it.id == itemId }) app.shop.refreshIfStale()
    }
    val item = items.firstOrNull { it.id == itemId }
    if (item == null) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            if (items.isEmpty()) CircularProgressIndicator(color = Tone.Info)
            else Text("Este preset não está mais na loja.", color = Tone.TextMuted)
        }
        return
    }
    ShopDetail(app, item, onBack, onOpenPhoto, onEditPhoto)
}

@Composable
private fun ShopDetail(
    app: TetherApp,
    item: ShopItem,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onEditPhoto: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val saved by app.shop.saved.collectAsState(initial = emptyMap())
    val savedId = saved[item.source]

    // 0: the look as the preset's author made it; 1: on one of your photos.
    var mode by rememberSaveable { mutableStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<ShopDownload?>(null) }
    var samplePhoto by remember { mutableStateOf<Photo?>(null) }

    val preview by produceState<Bitmap?>(null, item.id) { value = app.shop.preview(item, 1600) }
    var preset by remember { mutableStateOf<Preset?>(null) }
    var presetError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        runCatching { app.shop.preset(item) }
            .onSuccess { preset = it }
            .onFailure { presetError = it.message ?: "não deu para ler os ajustes" }
    }
    LaunchedEffect(Unit) { if (samplePhoto == null) samplePhoto = app.database.photoDao().latestWithImage() }
    val mine by produceState<Bitmap?>(null, preset, samplePhoto?.id) {
        val p = preset
        val uri = samplePhoto?.displayUri
        value = null
        if (p != null && uri != null) value = runCatching { app.looks.render(uri, p.settings, 1600) }.getOrNull()
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun run(label: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        scope.launch {
            runCatching { block() }.onFailure { toast(it.message ?: "Não deu certo") }
            busy = null
        }
    }

    val download = { run("Baixando…") { downloaded = app.shop.download(item) } }
    // No presets folder yet: pick one (the same folder as in Ajustes → Presets), then download.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            app.store.takePersistablePermission(uri)
            run("Baixando…") {
                app.settings.setOrClear(SettingKeys.presetsFolder, uri.toString())
                app.presets.attachFolder()
                downloaded = app.shop.download(item)
            }
        }
    }

    val picture: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier.background(Color.Black).pointerInput(mode) {
                // Hold on "Na minha foto" to see the photo without the preset.
                detectTapGestures(onPress = {
                    holding = true
                    tryAwaitRelease()
                    holding = false
                })
            },
            contentAlignment = Alignment.Center,
        ) {
            when {
                mode == 0 && preview != null ->
                    Image(preview!!.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                mode == 0 -> CircularProgressIndicator(color = Tone.Info)
                samplePhoto == null -> Text(
                    "Ainda não há fotos no app. Quando chegarem fotos da câmera, o preset aparece aplicado nelas aqui.",
                    color = Tone.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                holding -> AsyncImage(model = samplePhoto?.displayUri, contentDescription = "Original", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                mine != null -> Image(mine!!.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                presetError != null -> Text(presetError!!, color = Tone.Error, modifier = Modifier.padding(24.dp))
                else -> CircularProgressIndicator(color = Tone.Info)
            }
            if (mode == 1 && samplePhoto != null) {
                Text(
                    if (holding) "Original" else "Segure para ver o original",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                        .clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }

    val panel: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Prévia do preset", mode == 0) { mode = 0 }
                ModeChip("Na minha foto", mode == 1) { mode = 1 }
            }
            if (savedId != null) {
                StatusLine(Icons.Outlined.OfflinePin, "Em Meus presets › ${item.categoryLabel}. Funciona sem internet no live view, modo cliente e visualizador.")
            } else {
                StatusLine(Icons.Outlined.BookmarkAdd, "Salve para usar sem internet em todo o app.")
            }
            Button(
                onClick = { choosing = true },
                enabled = busy == null && presetError == null,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Text("  Usar numa foto", style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    icon = if (savedId != null) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                    label = if (savedId != null) "Salvo" else "Salvar",
                    enabled = busy == null && savedId == null && presetError == null,
                    modifier = Modifier.weight(1f),
                ) {
                    run("Salvando…") {
                        app.shop.save(item)
                        toast("Salvo em Meus presets › ${item.categoryLabel}")
                    }
                }
                ActionTile(Icons.Outlined.Download, "Baixar DNG + XMP", busy == null, Modifier.weight(1f)) {
                    if (settings.presetsFolder == null) pickFolder.launch(null) else download()
                }
                ActionTile(
                    Icons.Outlined.AutoAwesome,
                    if (settings.sessionPresetId == savedId && savedId != null && settings.applyPresetToNew) "Nas fotos novas" else "Fotos novas",
                    busy == null && presetError == null,
                    Modifier.weight(1f),
                ) {
                    run("Aplicando…") {
                        val id = app.shop.save(item)
                        app.settings.set(SettingKeys.sessionPresetId, id)
                        app.settings.set(SettingKeys.applyPresetToNew, true)
                        app.settings.set(SettingKeys.sessionAdjust, "")
                        toast("As próximas fotos da câmera chegam com ${item.name}")
                    }
                }
            }
            busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.Info) }
            Text(
                "Arquivo ${item.fileName} · ${if (settings.presetsFolder == null) "Baixar pede a pasta de presets" else "Baixar salva em JR_LR_Presets, na pasta de presets"}",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Tone.Background)) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Tone.Text) }
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, color = Tone.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.categoryLabel, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    picture(Modifier.weight(1f).fillMaxHeight())
                    panel(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).navigationBarsPadding())
                }
            } else {
                picture(Modifier.fillMaxWidth().weight(1f))
                panel(Modifier.fillMaxWidth().navigationBarsPadding())
            }
        }
    }

    if (choosing) {
        PhotoChooser(
            app = app,
            selected = samplePhoto,
            onSelect = { samplePhoto = it; mode = 1 },
            onDismiss = { choosing = false },
            onApply = { photo, edit ->
                choosing = false
                run("Aplicando…") {
                    val id = app.shop.save(item)
                    app.looks.setLook(photo, id, null)
                    app.presets.markUsed(id)
                    if (edit) onEditPhoto(photo.id) else onOpenPhoto(photo.id)
                }
            },
        )
    }

    downloaded?.let { d ->
        AlertDialog(
            onDismissRequest = { downloaded = null },
            containerColor = Tone.Surface,
            icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Tone.Accent) },
            title = { Text("Baixado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Na pasta de presets (${app.store.describeTree(settings.presetsFolder)}):", color = Tone.TextMuted)
                    d.dng?.let { Text("${d.folder}$it", color = Tone.Text) }
                    Text("${d.folder}${d.xmp}", color = Tone.Text)
                    Text("O .xmp abre no Lightroom do celular e do PC; o .dng importa no Lightroom mobile.", color = Tone.TextMuted, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = { downloaded = null }) { Text("OK") } },
        )
    }
}

/** Pick the photo to use the preset on: the app's photos (the ones that came from the camera). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoChooser(
    app: TetherApp,
    selected: Photo?,
    onSelect: (Photo) -> Unit,
    onDismiss: () -> Unit,
    onApply: (Photo, edit: Boolean) -> Unit,
) {
    val photos by remember { app.database.photoDao().observeAll() }.collectAsState(initial = null)
    var pick by remember { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Tone.Text)
                Text("  Escolha a foto", style = MaterialTheme.typography.titleMedium, color = Tone.Text)
            }
            val list = photos?.filter { it.displayUri != null }
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Tone.Info) }
                list.isEmpty() -> Text(
                    "Nenhuma foto no app ainda. As fotos da câmera aparecem aqui assim que chegam pelo cabo.",
                    color = Tone.TextMuted,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxWidth().height(360.dp).padding(top = 12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list, key = { it.id }) { photo ->
                        val on = pick?.id == photo.id
                        AsyncImage(
                            model = photo.displayUri,
                            contentDescription = photo.savedBase,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.extraSmall)
                                .then(if (on) Modifier.border(BorderStroke(3.dp, Tone.Accent), MaterialTheme.shapes.extraSmall) else Modifier)
                                .clickable { pick = photo; onSelect(photo) },
                        )
                    }
                }
            }
            val chosen = pick
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { chosen?.let { onApply(it, true) } }, enabled = chosen != null, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = Tone.Text, modifier = Modifier.size(18.dp))
                    Text("  No editor", color = Tone.Text, maxLines = 1)
                }
                Button(
                    onClick = { chosen?.let { onApply(it, false) } },
                    enabled = chosen != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Aplicar e ver", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Tone.SurfaceHigh,
            labelColor = Tone.TextMuted,
            selectedContainerColor = Tone.Text,
            selectedLabelColor = Tone.Background,
        ),
        border = null,
    )
}

@Composable
private fun StatusLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Tone.Info, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.small).background(Tone.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Tone.Text else Tone.TextMuted)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Tone.Text else Tone.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
