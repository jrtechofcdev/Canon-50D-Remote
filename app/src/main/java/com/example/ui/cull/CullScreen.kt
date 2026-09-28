package com.example.ui.cull

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.ui.photos.RatingBar
import com.example.ui.photos.SheetChip
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class CullFilter(val label: String) {
    All("Todas"), Unrated("Não avaliadas"), Suggested("Sugeridas"), Blurry("Tremidas"), EyesClosed("Olhos fechados"), Rejected("Rejeitadas"),
}

fun List<Photo>.cullFiltered(filter: CullFilter): List<Photo> = when (filter) {
    CullFilter.All -> filter { !it.rejected }
    CullFilter.Unrated -> filter { !it.rejected && !it.favorite && it.rating == 0 }
    CullFilter.Suggested -> filter { !it.rejected && it.suggested }
    CullFilter.Blurry -> filter { !it.rejected && it.blurry }
    CullFilter.EyesClosed -> filter { !it.rejected && it.eyesClosed > 0 }
    CullFilter.Rejected -> filter { it.rejected }
}

/** What "Aceitar sugestões" changed, so it can be undone. */
private data class Snapshot(val favorite: Map<Long, Boolean>, val rejected: Map<Long, Boolean>)

/**
 * Fast culling of the session: swipe up rejects, swipe down keeps (favourite), 1-5 stars, compare two
 * frames side by side with a shared zoom, and accept the local analysis' suggestions in one go.
 */
@Composable
fun CullScreen(app: TetherApp, onBack: (() -> Unit)?, onOpenPhoto: (Long) -> Unit) {
    val all by remember { app.database.photoDao().observeAll() }.collectAsState(initial = null)
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val pending by app.culler.pending.collectAsState()
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(CullFilter.All) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<Snapshot?>(null) }

    val loaded = all ?: return
    // The current session, oldest first like a contact sheet; everything when the session is empty.
    val session = loaded.filter { it.session == settings.sessionKey }.ifEmpty { loaded }.sortedBy { it.capturedAt }
    val photos = session.cullFiltered(filter)
    val pager = rememberPagerState { photos.size }
    val current = photos.getOrNull(pager.currentPage.coerceAtMost(photos.lastIndex))
    val dao = app.database.photoDao()

    fun next() = scope.launch { if (pager.currentPage < photos.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) }
    fun reject(p: Photo) = scope.launch { dao.setRejected(listOf(p.id), !p.rejected); if (filter != CullFilter.Rejected) next() }
    fun keep(p: Photo) = scope.launch { dao.setFavorite(listOf(p.id), !p.favorite); if (!p.favorite) next() }

    LaunchedEffect(Unit) { app.culler.analyzeMissing(settings.sessionKey) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(Tone.Background).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Culling", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (photos.isEmpty()) "Nenhuma foto neste filtro"
                    else "${pager.currentPage + 1} de ${photos.size}" + if (pending > 0) " · analisando $pending…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.TextMuted,
                )
            }
            IconButton(onClick = { compare = !compare }) {
                Icon(Icons.Outlined.CompareArrows, contentDescription = "Comparar", tint = if (compare) Tone.Accent else Tone.Text)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mais opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Tone.SurfaceHigh) {
                    if (settings.cullSuggestions) DropdownMenuItem(text = { Text("Aceitar sugestões da análise") }, onClick = { menu = false; confirm = true })
                    DropdownMenuItem(text = { Text("Analisar fotos sem nota") }, onClick = { menu = false; scope.launch { app.culler.analyzeMissing(settings.sessionKey) } })
                    current?.let { p -> DropdownMenuItem(text = { Text("Abrir no visualizador") }, onClick = { menu = false; onOpenPhoto(p.id) }) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Tone.Background).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CullFilter.entries.forEach { f ->
                val count = session.cullFiltered(f).size
                SheetChip("${f.label} $count", filter == f) { filter = f }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (photos.isEmpty()) {
                Text(
                    if (session.isEmpty()) {
                        "Cada foto que chega é analisada aqui no celular, sem internet: tremidas, olhos fechados e a melhor de cada pose. " +
                            "Depois é só arrastar para cima para rejeitar e para baixo para marcar."
                    } else "Nenhuma foto neste filtro.",
                    color = Tone.TextMuted,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else if (compare && current != null) {
                val other = photos.getOrNull(pager.currentPage + 1) ?: photos.getOrNull(pager.currentPage - 1)
                CompareView(current, other)
            } else {
                HorizontalPager(state = pager, key = { photos[it].id }, modifier = Modifier.fillMaxSize()) { page ->
                    val p = photos[page]
                    SwipeCard(p, onUp = { reject(p) }, onDown = { keep(p) })
                }
            }
            current?.let { Badges(it, settings, Modifier.align(Alignment.TopStart).padding(12.dp)) }
        }

        current?.let { p ->
            Column(
                Modifier.fillMaxWidth().background(Tone.Background)
                    .then(if (onBack != null) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { reject(p) }) {
                        Icon(Icons.Outlined.Close, contentDescription = if (p.rejected) "Desfazer rejeição" else "Rejeitar", tint = if (p.rejected) Tone.Error else Tone.Text)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (settings.ratingEnabled) RatingBar(p.rating) { stars ->
                            scope.launch { dao.setRating(listOf(p.id), if (stars == p.rating) 0 else stars); next() }
                        }
                    }
                    IconButton(onClick = { keep(p) }) {
                        Icon(if (p.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Favoritar", tint = if (p.favorite) Tone.Accent else Tone.Text)
                    }
                }
                Text(
                    "↑ rejeita  ·  ↓ marca  ·  nenhum arquivo é apagado",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                undo?.let { snap ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sugestões aplicadas.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                snap.favorite.forEach { (id, v) -> dao.setFavorite(listOf(id), v) }
                                snap.rejected.forEach { (id, v) -> dao.setRejected(listOf(id), v) }
                                undo = null
                            }
                        }) { Text("Desfazer") }
                    }
                }
            }
        }
    }

    if (confirm) {
        val keepList = session.filter { it.suggested && !it.rejected && !it.favorite }
        val dropList = session.filter { !it.rejected && !it.favorite && (it.blurry || it.eyesClosed > 0) }
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Tone.Surface,
            title = { Text("Aceitar sugestões?") },
            text = {
                Text(
                    "Marca ${keepList.size} foto(s) sugerida(s) como favoritas e rejeita ${dropList.size} tremida(s) ou com olhos fechados. " +
                        "Fotos que você já marcou não mudam. Dá para desfazer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        undo = Snapshot(keepList.associate { it.id to it.favorite }, dropList.associate { it.id to it.rejected })
                        dao.setFavorite(keepList.map { it.id }, true)
                        dao.setRejected(dropList.map { it.id }, true)
                    }
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

/** The photo; a vertical fling past a third of the height rejects (up) or keeps (down). */
@Composable
private fun SwipeCard(photo: Photo, onUp: () -> Unit, onDown: () -> Unit) {
    val offset = remember(photo.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val limit = size.height / 5f
                        val v = offset.value
                        when {
                            v < -limit -> onUp()
                            v > limit -> onDown()
                        }
                        scope.launch { offset.animateTo(0f) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onVerticalDrag = { _, dy -> scope.launch { offset.snapTo(offset.value + dy) } },
                )
            }
            .graphicsLayer {
                translationY = offset.value
                alpha = 1f - (abs(offset.value) / (size.height.coerceAtLeast(1f))).coerceIn(0f, 0.6f)
            },
    ) {
        AsyncImage(model = photo.displayUri, contentDescription = photo.savedBase, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        val hint = when {
            offset.value < -40 -> "Rejeitar" to Tone.Error
            offset.value > 40 -> "Marcar" to Tone.Accent
            else -> null
        }
        hint?.let { (label, color) ->
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                modifier = Modifier.align(Alignment.Center).clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Two frames, one above the other, zoomed and panned together to compare focus. */
@Composable
private fun CompareView(a: Photo, b: Photo?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, delta, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        pan = if (scale == 1f) Offset.Zero else pan + delta
    }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; pan = Offset.Zero } else scale = 3f }) }
            .transformable(state),
    ) {
        listOfNotNull(a, b).forEach { p ->
            Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.extraSmall)) {
                AsyncImage(
                    model = p.displayUri,
                    contentDescription = p.savedBase,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y },
                )
                Text(
                    p.savedBase + (p.score?.let { " · nota ${it.toInt()}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun Badges(p: Photo, settings: Settings, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (p.rejected) Badge("Rejeitada", Tone.Error)
        if (settings.cullSuggestions && p.suggested) Badge("Sugerida", Tone.Accent)
        if (p.blurry) Badge("Tremida", Tone.Error)
        if (settings.eyesCheck && p.eyesClosed > 0) Badge("Olhos fechados ${p.eyesClosed}/${p.faces ?: p.eyesClosed}", Tone.Error)
        if (p.burstGroup != null && !p.suggested) Badge("Parecida", Tone.Info)
        p.score?.let { Badge("Nota ${it.toInt()}", Tone.TextMuted) }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
