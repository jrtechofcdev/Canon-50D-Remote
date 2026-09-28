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
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
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

    var last by remember { mutableStateOf<LastCull?>(null) }

    fun next() = scope.launch { if (pager.currentPage < photos.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) }

    /**
     * Applies a choice and moves on. When the choice takes the photo out of the current filter the
     * list itself slides the next one in, so the pager must not advance too (that skipped a photo).
     */
    fun apply(p: Photo, rejected: Boolean, favorite: Boolean, label: String) = scope.launch {
        last = LastCull(p, label)
        dao.setRejected(listOf(p.id), rejected)
        dao.setFavorite(listOf(p.id), favorite)
        val after = p.copy(rejected = rejected, favorite = favorite)
        val stays = listOf(after).cullFiltered(filter).isNotEmpty()
        if (stays) next()
    }
    fun reject(p: Photo) = apply(p, rejected = !p.rejected, favorite = p.favorite && p.rejected, label = if (p.rejected) "voltou" else "rejeitada")
    fun keep(p: Photo) = apply(p, rejected = false, favorite = !p.favorite, label = if (p.favorite) "desmarcada" else "marcada")
    // A swipe always sets (never toggles): up is out, down is in.
    fun swipeOut(p: Photo) = apply(p, rejected = true, favorite = false, label = "rejeitada")
    fun swipeIn(p: Photo) = apply(p, rejected = false, favorite = true, label = "marcada")

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
                    SwipeCard(p, onUp = { swipeOut(p) }, onDown = { swipeIn(p) })
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
                last?.let { l ->
                    // The last choice, with undo: a swipe is quick, so a slip is easy to take back.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${l.before.savedBase} ${l.label}", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = {
                            scope.launch {
                                dao.setRejected(listOf(l.before.id), l.before.rejected)
                                dao.setFavorite(listOf(l.before.id), l.before.favorite)
                                last = null
                            }
                        }) { Text("Desfazer") }
                    }
                } ?: Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = Tone.Error, modifier = Modifier.size(16.dp))
                    Text(" rejeita   ", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = Tone.Accent, modifier = Modifier.size(16.dp))
                    Text(" marca   ·   nada é apagado", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
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

private data class LastCull(val before: Photo, val label: String)

/**
 * The photo as a card that follows the finger. Up throws it out (red stamp), down keeps it (mint
 * heart). The stamp grows with the drag and locks in, with a buzz, once far enough; letting go there,
 * or flicking, sends the card off the screen. Short of that it springs back.
 */
@Composable
private fun SwipeCard(photo: Photo, onUp: () -> Unit, onDown: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var dragY by remember(photo.id) { mutableFloatStateOf(0f) }
    var armed by remember(photo.id) { mutableIntStateOf(0) }
    val red = Tone.Error
    val mint = Tone.Accent

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val limit = h * 0.16f
        val progress = (abs(dragY) / limit).coerceIn(0f, 1f)
        val up = dragY < 0

        // Edge glow on the side the card is heading to.
        if (dragY != 0f) {
            val glow = (if (up) red else mint).copy(alpha = 0.35f * progress)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        if (up) listOf(glow, Color.Transparent, Color.Transparent) else listOf(Color.Transparent, Color.Transparent, glow),
                    ),
                ),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .pointerInput(photo.id) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragY += dy
                            val now = when {
                                dragY < -limit -> -1
                                dragY > limit -> 1
                                else -> 0
                            }
                            if (now != armed) {
                                armed = now
                                if (now != 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().y
                            val dir = when {
                                dragY < -limit || (velocity < -FLING && dragY < 0) -> -1
                                dragY > limit || (velocity > FLING && dragY > 0) -> 1
                                else -> 0
                            }
                            scope.launch {
                                if (dir != 0) {
                                    animate(dragY, dir * h * 1.2f, animationSpec = tween(220)) { v, _ -> dragY = v }
                                    if (dir < 0) onUp() else onDown()
                                    // The card stays out of sight until the list moves on.
                                    kotlinx.coroutines.delay(250)
                                    dragY = 0f
                                } else {
                                    animate(dragY, 0f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)) { v, _ -> dragY = v }
                                }
                                armed = 0
                            }
                        },
                        onDragCancel = {
                            scope.launch { animate(dragY, 0f, animationSpec = spring(dampingRatio = 0.55f)) { v, _ -> dragY = v } }
                            armed = 0
                        },
                    )
                }
                .graphicsLayer {
                    translationY = dragY
                    rotationZ = dragY / h * 10f
                    val shrink = 1f - 0.07f * progress
                    scaleX = shrink
                    scaleY = shrink
                }
                .clip(MaterialTheme.shapes.large)
                .background(Color(0xFF0C0C0C)),
        ) {
            AsyncImage(model = photo.displayUri, contentDescription = photo.savedBase, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }

        if (progress > 0.05f) {
            val color = if (up) red else mint
            val locked = armed != 0
            // The stamp: an outlined label that fills in once the choice is locked.
            Row(
                Modifier
                    .align(if (up) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(vertical = 48.dp)
                    .graphicsLayer {
                        val grow = 0.7f + 0.35f * progress
                        scaleX = grow
                        scaleY = grow
                        alpha = progress
                        rotationZ = if (up) -10f else 10f
                    }
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (locked) color else Color.Black.copy(alpha = 0.55f))
                    .border(3.dp, color, MaterialTheme.shapes.medium)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (up) Icons.Outlined.Close else Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = if (locked) Color.Black else color,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (up) "REJEITAR" else "MARCAR",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (locked) Color.Black else color,
                )
            }
        }
    }
}

private const val FLING = 1800f

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
