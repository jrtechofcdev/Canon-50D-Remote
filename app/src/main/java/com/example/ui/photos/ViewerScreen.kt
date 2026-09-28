package com.example.ui.photos

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.analysis.Analysis
import com.example.core.analysis.ImageAnalyzer
import com.example.core.data.Photo
import com.example.ui.PhotoActions
import com.example.ui.ShareSet
import com.example.core.preset.Preset
import com.example.ui.preset.LookImage
import com.example.ui.preset.PresetStrip
import com.example.ui.theme.Tone
import androidx.compose.material.icons.outlined.Tune
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ViewerScreen(
    app: TetherApp,
    actions: PhotoActions,
    startId: Long,
    onBack: () -> Unit,
    onCalibrate: (Long) -> Unit,
    onCrop: (Long) -> Unit = {},
) {
    val photos by remember { app.database.photoDao().observeAll() }.collectAsState(initial = null)
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    val s by app.settings.settings.collectAsState(initial = null)
    val list = photos ?: return
    val settings = s ?: return
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val start = list.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { list.size }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<Photo?>(null) }
    val zoomed = remember { mutableStateMapOf<Long, Boolean>() }
    var showClipping by remember { mutableStateOf(settings.highlightAlert) }
    var noteFor by remember { mutableStateOf<Photo?>(null) }
    var whatsFor by remember { mutableStateOf<Photo?>(null) }

    // Slide to a frame that just arrived. New photos are inserted at index 0; the pager keeps the
    // current photo in place by key, so "was on the latest" means we were on index 0 before it.
    val currentList by rememberUpdatedState(list)
    val currentSettings by rememberUpdatedState(settings)
    LaunchedEffect(Unit) {
        var latestId = currentList.first().id
        app.tether.saved.collect { saved ->
            val cfg = currentSettings
            val viewingId = currentList.getOrNull(pager.currentPage)?.id
            val wasOnLatest = viewingId == latestId
            latestId = saved.id
            if (!cfg.autoSlideToNew) return@collect
            if (cfg.autoSlideOnlyAtLatest && !wasOnLatest) return@collect
            if (viewingId != null && zoomed[viewingId] == true) return@collect
            // Wait for the new row to reach the list before scrolling to it.
            repeat(20) {
                if (currentList.firstOrNull()?.id == saved.id) {
                    pager.animateScrollToPage(0)
                    return@collect
                }
                kotlinx.coroutines.delay(50)
            }
        }
    }

    // One reduced decode per viewed photo feeds both the histogram and the highlight mask.
    val viewing = list.getOrNull(pager.currentPage)
    val analysis = if (settings.histogramEnabled || showClipping) {
        rememberAnalysis(viewing?.displayUri, settings.highlightThreshold)
    } else null

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { list[it].id }) { page ->
            val photo = list[page]
            val mask = if (showClipping && photo.id == viewing?.id && photo.crop == null) analysis?.clippedMask else null
            PhotoPage(app, photo, presets, mask) { zoomed[photo.id] = it }
        }

        val current = list.getOrNull(pager.currentPage) ?: return@Box

        Row(
            Modifier.fillMaxWidth().background(Tone.Background.copy(alpha = 0.75f)).statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            Column(Modifier.weight(1f)) {
                Text(current.savedBase, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(current.capturedAt)) + " · " + current.session,
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.TextMuted,
                )
            }
            IconButton(onClick = { onCrop(current.id) }) {
                Icon(Icons.Outlined.Crop, contentDescription = "Recortar", tint = if (current.crop != null) Tone.Accent else Tone.Text)
            }
            IconButton(onClick = { showClipping = !showClipping }) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = if (showClipping) "Esconder alerta de estouro" else "Mostrar alerta de estouro",
                    tint = if (showClipping) Tone.Info else Tone.TextMuted,
                )
            }
            if (settings.notesEnabled) {
                IconButton(onClick = { noteFor = current }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.StickyNote2,
                        contentDescription = "Nota",
                        tint = if (current.note != null || current.audioNote != null) Tone.Accent else Tone.Text,
                    )
                }
            }
            IconButton(onClick = { scope.launch { app.database.photoDao().setFavorite(current.id, !current.favorite) } }) {
                Icon(
                    if (current.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorita",
                    tint = Tone.Text,
                )
            }
            IconButton(onClick = { confirmDelete = current }) { Icon(Icons.Outlined.Delete, contentDescription = "Apagar do celular") }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Tone.Background.copy(alpha = 0.85f))
                .navigationBarsPadding().padding(16.dp),
        ) {
            com.example.ui.cull.Badges(current, settings, Modifier.padding(bottom = 8.dp))
            if (settings.viewerPresets && presets.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        PresetStrip(presets, current.presetId) { picked ->
                            scope.launch { app.looks.setLook(current, picked?.id, null) }
                        }
                    }
                    TextButton(onClick = { onCalibrate(current.id) }) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = Tone.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Calibrar", color = Tone.Accent)
                    }
                }
                if (current.adjust != null) {
                    Text("Calibrada só nesta foto", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                }
            }
            if (settings.ratingEnabled) {
                RatingBar(current.rating) { stars ->
                    scope.launch { app.database.photoDao().setRating(current.id, if (stars == current.rating) 0 else stars) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(current.shutter, current.aperture, current.iso).joinToString("   ").ifEmpty { "Sem dados EXIF" },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        listOfNotNull(fileSummary(current), current.moment).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.TextMuted,
                    )
                    current.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (settings.histogramEnabled) {
                    Histogram(analysis, settings.histogramRgb, Modifier.width(128.dp).height(52.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetMenuButton(
                    label = "Lightroom",
                    primary = true,
                    sets = actions.availableSets(current, settings, includeLight = false),
                    modifier = Modifier.weight(1f),
                ) { scope.launch { actions.openInLightroom(listOf(current), it, settings) } }
                OutlinedButton(onClick = { whatsFor = current }, modifier = Modifier.weight(1f)) { Text("WhatsApp", maxLines = 1) }
                SetMenuButton(
                    label = "Enviar",
                    primary = false,
                    sets = actions.availableSets(current, settings),
                    modifier = Modifier.weight(1f),
                ) { scope.launch { actions.share(listOf(current), it, settings) } }
                OutlinedButton(onClick = { actions.openWith(current) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Abrir com")
                }
            }
        }
    }

    noteFor?.let { photo -> NoteSheet(app, photo, onDismiss = { noteFor = null }) }
    whatsFor?.let { photo -> WhatsAppSheet(app, actions, settings, listOf(photo), onDismiss = { whatsFor = null }) }

    confirmDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Tone.Surface,
            title = { Text("Apagar do celular?") },
            text = { Text("Os arquivos de ${photo.savedBase} saem do celular. O original continua no cartão da câmera.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch(Dispatchers.IO) { deletePhoto(app, photo) }
                }) { Text("Apagar", color = Tone.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun RatingBar(rating: Int, onRate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..5) {
            IconButton(
                onClick = { onRate(star) },
                modifier = Modifier.size(40.dp).semantics { contentDescription = "$star estrela(s)" },
            ) {
                Icon(
                    if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (star <= rating) Tone.Text else Tone.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun rememberAnalysis(uri: String?, threshold: Int): Analysis? {
    val context = LocalContext.current
    val analysis by produceState<Analysis?>(initialValue = null, uri, threshold) {
        value = uri?.let { withContext(Dispatchers.Default) { ImageAnalyzer.analyze(context, it, threshold) } }
    }
    return analysis
}

/** A button that asks which files to send when there is more than one option. */
@Composable
private fun SetMenuButton(
    label: String,
    primary: Boolean,
    sets: List<ShareSet>,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    onPick: (ShareSet) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val onClick = { if (sets.size == 1) onPick(sets.first()) else open = true }
        val content: @Composable () -> Unit = {
            icon?.let { it(); Spacer(Modifier.width(8.dp)) }
            Text(label)
        }
        if (primary) {
            Button(
                onClick = onClick, enabled = sets.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { content() }
        } else {
            OutlinedButton(onClick = onClick, enabled = sets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { content() }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Tone.SurfaceHigh) {
            sets.forEach { set ->
                DropdownMenuItem(text = { Text(set.label) }, onClick = {
                    open = false
                    onPick(set)
                })
            }
        }
    }
}

/** One page of the pager: zoomable photo with the blinking highlight mask on top (same transform). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoPage(app: TetherApp, photo: Photo, presets: List<Preset>, mask: android.graphics.Bitmap?, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
        onZoomChanged(scale > 1f)
    }
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blinkAlpha",
    )

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Double tap toggles a 3× zoom to check focus.
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 3f
                    onZoomChanged(scale > 1f)
                })
            }
            // Only take gestures while zoomed, so swiping between photos keeps working at fit size.
            .transformable(state, lockRotationOnZoomPan = true, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        LookImage(app, photo, presets, Modifier.fillMaxSize())
        if (mask != null) {
            val image = remember(mask) { mask.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = "Áreas estouradas",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = blink },
            )
        }
    }
}

private fun fileSummary(photo: Photo): String {
    fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0)
    return listOfNotNull(
        photo.jpgUri?.let { "JPG ${mb(photo.jpgSize)}" },
        photo.rawUri?.let { "RAW ${mb(photo.rawSize)}" },
    ).joinToString(" · ")
}

@Composable
private fun Histogram(analysis: Analysis?, rgb: Boolean, modifier: Modifier) {
    val background = Tone.Surface
    val luma = Tone.Info
    Canvas(modifier.background(background)) {
        val a = analysis ?: return@Canvas
        val channels = if (rgb) {
            listOf(a.red to Color(0xFFE5484D), a.green to Color(0xFF46A758), a.blue to Color(0xFF3E63DD))
        } else {
            listOf(a.luma to luma)
        }
        val max = channels.maxOf { (bins, _) -> bins.max() }.takeIf { it > 0 } ?: return@Canvas
        val barWidth = size.width / 64
        for ((bins, color) in channels) {
            bins.forEachIndexed { i, count ->
                val h = size.height * count / max
                // Additive blend so overlapping channels read as white/cyan/magenta like a camera histogram.
                drawRect(color, topLeft = Offset(i * barWidth, size.height - h), size = Size(barWidth, h),
                    blendMode = if (rgb) BlendMode.Plus else BlendMode.SrcOver)
            }
        }
    }
}
