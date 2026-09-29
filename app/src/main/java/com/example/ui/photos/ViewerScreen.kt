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
import androidx.compose.material.icons.outlined.RotateRight
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
import com.example.ui.preset.PresetPicker
import com.example.ui.theme.Tone
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.RotateButton
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

    // One tap hides everything but the photo; another brings it back.
    var chrome by remember { mutableStateOf(true) }
    // A vertical photo gets a standing screen, a horizontal one a lying screen (Ajustes → Tela).
    com.example.ui.components.FollowPhotoOrientation(app, viewing, settings.rotateWithPhoto, settings.screenOrientation)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { pagerKey(currentList, it) }) { page ->
            val photo = currentList.getOrNull(page) ?: return@HorizontalPager
            val mask = if (showClipping && photo.id == viewing?.id && photo.crop == null) analysis?.clippedMask else null
            PhotoPage(app, photo, presets, mask, onTap = { chrome = !chrome }) { zoomed[photo.id] = it }
        }

        val current = list.getOrNull(pager.currentPage) ?: return@BoxWithConstraints

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Tone.Background.copy(alpha = 0.75f)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                Column(Modifier.weight(1f)) {
                    Text(current.savedBase, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(current.capturedAt)) + " · " + current.session,
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RotateButton(app, settings.screenOrientation)
                IconButton(onClick = { showClipping = !showClipping }) {
                    Icon(
                        Icons.Outlined.WbSunny,
                        contentDescription = if (showClipping) "Esconder alerta de estouro" else "Mostrar alerta de estouro",
                        tint = if (showClipping) Tone.Info else Tone.TextMuted,
                    )
                }
                IconButton(onClick = { scope.launch { app.database.photoDao().setFavorite(current.id, !current.favorite) } }) {
                    Icon(
                        if (current.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorita",
                        tint = if (current.favorite) Tone.Accent else Tone.Text,
                    )
                }
                IconButton(onClick = { confirmDelete = current }) { Icon(Icons.Outlined.Delete, contentDescription = "Apagar do celular") }
            }
        }

        val panel: @Composable (Modifier) -> Unit = { m ->
            Column(m.background(Tone.Background.copy(alpha = 0.88f)).padding(vertical = 10.dp)) {
                // Info: badges, exposure, file and histogram.
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        com.example.ui.cull.Badges(current, settings, Modifier.padding(bottom = 4.dp))
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
                        Histogram(analysis, settings.histogramRgb, Modifier.width(120.dp).height(48.dp).clip(MaterialTheme.shapes.extraSmall))
                    }
                }
                if (settings.ratingEnabled) {
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        RatingBar(current.rating) { stars ->
                            scope.launch { app.database.photoDao().setRating(current.id, if (stars == current.rating) 0 else stars) }
                        }
                    }
                }
                if (settings.viewerPresets && presets.isNotEmpty()) {
                    // Presets and Calibrar on one line, same tile size, so nothing sits out of step.
                    Row(verticalAlignment = Alignment.Bottom) {
                        PresetPicker(
                            app, presets, current.presetId, current.displayUri,
                            onPick = { picked -> scope.launch { app.looks.setLook(current, picked?.id, null) } },
                            modifier = Modifier.weight(1f),
                        )
                        ActionTile(Icons.Outlined.Tune, if (current.adjust != null) "Calibrada" else "Calibrar", accent = true, box = 64.dp, modifier = Modifier.padding(end = 4.dp)) {
                            onCalibrate(current.id)
                        }
                    }
                }
                HorizontalDivider(color = Tone.Line, modifier = Modifier.padding(vertical = 6.dp))
                // What to do with the photo, as icons with their names.
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SetMenuTile(
                        label = "Lightroom",
                        sets = actions.availableSets(current, settings, includeLight = false),
                        icon = { LightroomMark() },
                    ) { scope.launch { actions.openInLightroom(listOf(current), it, settings) } }
                    ActionTile(Icons.AutoMirrored.Outlined.Send, "WhatsApp", accent = true) { whatsFor = current }
                    SetMenuTile(
                        label = "Enviar",
                        sets = actions.availableSets(current, settings),
                        icon = { Icon(Icons.Outlined.Share, contentDescription = null, tint = Tone.Text) },
                    ) { scope.launch { actions.share(listOf(current), it, settings) } }
                    ActionTile(Icons.Outlined.Crop, "Recortar", selected = current.crop != null) { onCrop(current.id) }
                    // A quarter turn of the photo itself (for frames the camera saved lying down).
                    ActionTile(Icons.Outlined.RotateRight, "Girar") {
                        scope.launch { app.looks.setCrop(current, com.example.core.render.PhotoShape.turnedClockwise(app.looks.cropOf(current))) }
                    }
                    if (settings.notesEnabled) {
                        ActionTile(Icons.AutoMirrored.Outlined.StickyNote2, "Nota", selected = current.note != null || current.audioNote != null) { noteFor = current }
                    }
                    ActionTile(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir com") { actions.openWith(current) }
                }
            }
        }
        AnimatedVisibility(
            chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = if (landscape) Modifier.align(Alignment.CenterEnd) else Modifier.align(Alignment.BottomCenter),
        ) {
            if (landscape) {
                // Lying down the panel moves to the side, so the photo keeps the full height.
                panel(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).navigationBarsPadding())
            } else {
                panel(Modifier.fillMaxWidth().navigationBarsPadding())
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

/**
 * Page key read from the newest list. The pager keeps the key function it was first given, so a
 * list captured at that moment went stale: a photo arriving with the viewer open asked for index 12
 * of a 12-item list and closed the app (IndexOutOfBoundsException in ViewerScreen, seen in the log).
 * A page that is momentarily past the end gets a key of its own until the list catches up.
 */
fun pagerKey(list: List<com.example.core.data.Photo>, index: Int): Any = list.getOrNull(index)?.id ?: "fim-$index"

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

/** An icon with its name under it: the viewer's actions, readable at a glance. */
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    box: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: () -> Unit,
) {
    ActionTileBase(label, modifier, enabled, onClick, box) {
        Icon(icon, contentDescription = null, tint = if (accent || selected) Tone.Accent else Tone.Text)
    }
}

@Composable
private fun ActionTileBase(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit, box: androidx.compose.ui.unit.Dp = 44.dp, icon: @Composable () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.small).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(box).clip(MaterialTheme.shapes.medium).background(Tone.SurfaceHigh), contentAlignment = Alignment.Center) { icon() }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The "Lr" square photographers know from the app, drawn in the app's own colours. */
@Composable
internal fun LightroomMark() {
    Box(
        Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall).border(1.5.dp, Tone.Info, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) { Text("Lr", style = MaterialTheme.typography.labelMedium, color = Tone.Info) }
}

/** An action tile that asks which files to use when there is more than one option. */
@Composable
private fun SetMenuTile(label: String, sets: List<ShareSet>, icon: @Composable () -> Unit, onPick: (ShareSet) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionTileBase(label, Modifier, sets.isNotEmpty(), { if (sets.size == 1) onPick(sets.first()) else open = true }, icon = icon)
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

/**
 * One page of the pager: zoomable photo with the blinking highlight mask on top (same transform).
 * Tap shows or hides the controls, double tap zooms 3× to check focus, and holding a finger shows
 * the photo before the look and the crop (let go to see it after).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoPage(
    app: TetherApp,
    photo: Photo,
    presets: List<Preset>,
    mask: android.graphics.Bitmap?,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var before by remember { mutableStateOf(false) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
        onZoomChanged(scale > 1f)
    }
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blinkAlpha",
    )
    val edited = photo.presetId != null || photo.adjust != null || photo.crop != null

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(photo.id, edited) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                // Zoom into the spot that was tapped.
                                scale = 3f
                                offset = (Offset(size.width / 2f, size.height / 2f) - tap) * 2f
                            }
                            onZoomChanged(scale > 1f)
                        },
                        onLongPress = { if (edited) before = true },
                        onPress = {
                            tryAwaitRelease()
                            before = false
                        },
                    )
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
            if (before) {
                coil.compose.AsyncImage(model = photo.displayUri, contentDescription = "Antes", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                LookImage(app, photo, presets, Modifier.fillMaxSize())
            }
            if (mask != null && !before) {
                val image = remember(mask) { mask.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "Áreas estouradas",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = blink },
                )
            }
        }
        if (before) {
            Text(
                "Antes",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 6.dp),
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
