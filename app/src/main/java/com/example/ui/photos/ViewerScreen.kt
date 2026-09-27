package com.example.ui.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.ui.PhotoActions
import com.example.ui.ShareSet
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ViewerScreen(app: TetherApp, actions: PhotoActions, startId: Long, onBack: () -> Unit) {
    val photos by remember { app.database.photoDao().observeAll() }.collectAsState(initial = null)
    val list = photos ?: return
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val start = list.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { list.size }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<Photo?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { list[it].id }) { page ->
            ZoomableImage(list[page])
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
            IconButton(onClick = { scope.launch { app.database.photoDao().setFavorite(current.id, !current.favorite) } }) {
                Icon(
                    if (current.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Favorita",
                    tint = if (current.favorite) Tone.Accent else Tone.Text,
                )
            }
            IconButton(onClick = { confirmDelete = current }) { Icon(Icons.Outlined.Delete, contentDescription = "Apagar do celular") }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Tone.Background.copy(alpha = 0.85f))
                .navigationBarsPadding().padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(current.shutter, current.aperture, current.iso).joinToString("   ").ifEmpty { "Sem dados EXIF" },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(fileSummary(current), style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
                Histogram(current.displayUri, Modifier.width(120.dp).height(48.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetMenuButton(
                    label = "Lightroom",
                    primary = true,
                    sets = actions.availableSets(current),
                    modifier = Modifier.weight(1f),
                ) { actions.openInLightroom(current, it) }
                SetMenuButton(
                    label = "Enviar",
                    primary = false,
                    icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    sets = actions.availableSets(current),
                    modifier = Modifier.weight(1f),
                ) { actions.share(current, it) }
                OutlinedButton(onClick = { actions.openWith(current) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Abrir com")
                }
            }
        }
    }

    confirmDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Tone.Surface,
            title = { Text("Apagar do celular?") },
            text = { Text("Os arquivos de ${photo.savedBase} saem do celular. O original continua no cartão da câmera.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch(Dispatchers.IO) {
                        listOfNotNull(photo.jpgUri, photo.rawUri).forEach(app.store::delete)
                        photo.previewPath?.let { java.io.File(it).delete() }
                        app.database.photoDao().delete(photo.id)
                    }
                }) { Text("Apagar", color = Tone.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** A button that asks which files to send when the frame has both RAW and JPG on the phone. */
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(photo: Photo) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = photo.displayUri,
        contentDescription = photo.savedBase,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Double tap toggles 100%-ish zoom to check focus.
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 3f
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
    )
}

private fun fileSummary(photo: Photo): String {
    fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0)
    return listOfNotNull(
        photo.jpgUri?.let { "JPG ${mb(photo.jpgSize)}" },
        photo.rawUri?.let { "RAW ${mb(photo.rawSize)}" },
    ).joinToString(" · ")
}

/** Luminance histogram, drawn from a downscaled decode so it's quick even for 15 MP files. */
@Composable
private fun Histogram(uri: String?, modifier: Modifier) {
    val context = LocalContext.current
    val bins by produceState<IntArray?>(initialValue = null, uri) {
        value = uri?.let { withContext(Dispatchers.Default) { computeHistogram(context, it) } }
    }
    Canvas(modifier.background(Tone.Surface)) {
        val data = bins ?: return@Canvas
        val max = data.maxOrNull()?.takeIf { it > 0 } ?: return@Canvas
        val barWidth = size.width / data.size
        data.forEachIndexed { i, count ->
            val h = size.height * count / max
            drawRect(Tone.TextMuted, topLeft = Offset(i * barWidth, size.height - h), size = Size(barWidth, h))
        }
    }
}

private fun computeHistogram(context: Context, uri: String): IntArray? = runCatching {
    val resolver = context.contentResolver
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 256) sample *= 2
    val bitmap = resolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    bitmap.recycle()
    val bins = IntArray(64)
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val y = (r * 299 + g * 587 + b * 114) / 1000
        bins[y shr 2]++
    }
    bins
}.getOrNull()
