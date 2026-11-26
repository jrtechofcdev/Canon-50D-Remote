package com.example.ui.insta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.church.ChurchEvent
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.delivery.DeliveryStatus
import com.example.core.insta.IgCaption
import com.example.core.insta.IgCarousel
import com.example.core.insta.IgDraft
import com.example.core.insta.IgItem
import com.example.core.insta.IgStyle
import com.example.core.insta.centredAt
import com.example.core.insta.shapeLabel
import com.example.core.render.CropSpec
import com.example.ui.components.EmptyState
import com.example.ui.components.JrButton
import com.example.ui.components.JrButtonKind
import com.example.ui.components.JrChip
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SheetHeader
import com.example.ui.delivery.DeliveryCard
import com.example.ui.theme.Space
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Instagram (experimental): picks the photos of the open church event with the most potential for the
 * church's Instagram, all on the phone. The photographer approves, adjusts and sends them to the media page.
 */
@Composable
fun InstaScreen(app: TetherApp, onBack: (() -> Unit)?) {
    val settings by app.settings.settings.collectAsState(initial = null)
    val event by app.church.repo.dao.observeOpenEvent().collectAsState(initial = null)
    val s = settings ?: return
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
            ScreenHeader(title = "Instagram", subtitle = "Experimental" + (event?.let { " · ${it.name}" } ?: ""), onBack = onBack)
            val e = event
            if (e == null) {
                EmptyState(
                    "Nenhum evento aberto",
                    modifier = Modifier.padding(Space.l),
                    text = "A seleção para o Instagram trabalha com as fotos do evento aberto na Igreja. Abra um evento lá e volte aqui.",
                )
                return@Column
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.l, vertical = Space.s),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InstaBody(app, e, s)
                Spacer(Modifier.padding(bottom = 88.dp))
            }
        }
    }
}

@Composable
private fun InstaBody(app: TetherApp, e: ChurchEvent, s: Settings) {
    val scope = rememberCoroutineScope()
    val inEvent by app.church.repo.dao.observePhotos(e.id).collectAsState(initial = emptyList())
    val all by app.database.photoDao().observeAll().collectAsState(initial = emptyList())
    val byId = remember(all) { all.associateBy { it.id } }
    val photos = remember(inEvent, byId) { inEvent.filter { !it.hidden }.mapNotNull { byId[it.photoId] } }
    val draft by app.insta.draft.collectAsState()
    val progress by app.insta.progress.collectAsState()
    val d = draft?.takeIf { it.eventId == e.id }

    // Back in the screen after the app was closed: the post already sent comes back as the draft.
    LaunchedEffect(e.id, byId.isNotEmpty()) {
        if (byId.isNotEmpty() && d == null) app.insta.loadSent(e.id, e.igNotes, e.igCaption, byId)
    }

    // Analyse
    DeliveryCard {
        Text("Escolher as fotos", style = MaterialTheme.typography.titleMedium)
        Text(
            "${photos.size} fotos no evento. A análise roda no celular, sem internet e sem custo: louvor, oração, " +
                "ministração, grupos, crianças e a igreja cheia, com nitidez, luz e o estilo da igreja.",
            style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted,
        )
        val p = progress
        if (p != null) {
            LinearProgressIndicator(
                progress = { if (p.total == 0) 0f else p.done / p.total.toFloat() },
                color = Tone.Accent, trackColor = Tone.SurfaceHigh, modifier = Modifier.fillMaxWidth(),
            )
            val left = p.left(System.currentTimeMillis())?.let { ms -> " · falta cerca de ${minutes(ms)}" }.orEmpty()
            Text("${p.stage}: ${p.done} de ${p.total}$left", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        } else {
            JrButton(
                if (d == null) "Analisar" else "Analisar de novo",
                icon = Icons.Outlined.AutoAwesome,
                enabled = photos.isNotEmpty(),
                kind = if (d == null) JrButtonKind.Primary else JrButtonKind.Secondary,
                onClick = {
                    scope.launch {
                        val r = app.insta.analyze(photos, e.congregationId, s.instaSize) ?: return@launch
                        app.insta.startDraft(e.id, r, aspectsOf(app, r.candidates.map { it.photoId }))
                        app.insta.update { it.copy(caption = suggestion(app, e, it, s, 0), model = 0) }
                        if (r.picked.isEmpty()) app.notice("Nenhuma foto passou na análise. Confira se as fotos do evento estão nítidas.")
                    }
                },
            )
        }
    }

    if (d != null) {
        PostCard(app, d, s)
        CaptionCard(app, e, d, s)
        SendCard(app, e, d)
    }
    StyleCard(app, e)
}

/** Width / height of each photo, from the measures kept by the analysis. */
private suspend fun aspectsOf(app: TetherApp, ids: List<Long>): Map<Long, Float> = ids.mapNotNull { id ->
    app.database.instaDao().feature(id)?.let { com.example.core.insta.IgFeatures.decode(it.data) }?.let { id to it.aspect }
}.toMap()

private fun minutes(ms: Long): String {
    val m = (ms / 60_000).toInt()
    return if (m < 1) "menos de 1 minuto" else if (m == 1) "1 minuto" else "$m minutos"
}

private fun suggestion(app: TetherApp, e: ChurchEvent, d: IgDraft, s: Settings, index: Int): String {
    val voice = app.insta.voice(e.congregationId)
    val facts = IgCaption.Facts(
        event = e.name,
        church = e.congregation,
        at = d.chosen.minOfOrNull { it.capturedAt } ?: e.date,
        moments = d.moments,
        notes = d.notes,
        credit = s.instaCredit.ifBlank { voice?.credit.orEmpty() },
        hashtags = s.instaHashtags.ifBlank { voice?.hashtags.orEmpty().joinToString(" ") },
    )
    return IgCaption.suggest(facts, index, IgCaption.templates(s.instaTemplates))
}

// ---- the carousel ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostCard(app: TetherApp, d: IgDraft, s: Settings) {
    val all by app.database.photoDao().observeAll().collectAsState(initial = emptyList())
    val byId = remember(all) { all.associateBy { it.id } }
    var open by remember { mutableStateOf<Long?>(null) }
    DeliveryCard {
        val shape = d.chosen.firstOrNull()?.feed?.shapeLabel.orEmpty()
        Text("Carrossel · ${d.chosen.size} fotos", style = MaterialTheme.typography.titleMedium)
        Text(
            "Na ordem do post; a primeira é a capa" + (if (shape.isNotEmpty()) ", feed em $shape" else "") +
                ". Toque numa foto para ver o motivo, mudar a ordem ou o recorte.",
            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            d.chosen.forEachIndexed { i, item -> PickThumb(byId[item.photoId], item, number = i + 1) { open = item.photoId } }
        }
        if (d.others.isNotEmpty()) {
            Text("Outras boas · toque para pôr no post", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted, modifier = Modifier.padding(top = Space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                d.others.take(24).forEach { item ->
                    PickThumb(byId[item.photoId], item, number = null, enabled = d.chosen.size < IgCarousel.MAX_SIZE) { app.insta.add(item.photoId, IgCarousel.MAX_SIZE) }
                }
            }
        }
    }
    open?.let { id ->
        val item = d.chosen.firstOrNull { it.photoId == id }
        if (item == null) open = null else ItemSheet(app, d, item, byId[id], onDismiss = { open = null })
    }
}

@Composable
private fun PickThumb(photo: Photo?, item: IgItem, number: Int?, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(if (number != null) 96.dp else 72.dp).clip(MaterialTheme.shapes.extraSmall).background(Tone.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        AsyncImage(model = photo?.displayUri, contentDescription = item.moment.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (number != null) {
            Box(
                Modifier.align(Alignment.TopStart).padding(Space.xs).size(24.dp).clip(CircleShape)
                    .background(if (number == 1) Tone.Accent else Tone.Scrim),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.labelMedium, color = if (number == 1) Tone.OnAccent else Tone.OnPhoto)
            }
        } else {
            Box(Modifier.align(Alignment.TopEnd).padding(Space.xs).size(22.dp).clip(CircleShape).background(Tone.Scrim), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, contentDescription = "Pôr no post", tint = Tone.OnPhoto, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            "${item.score.toInt()} · ${item.moment.label}",
            style = MaterialTheme.typography.labelSmall, color = Tone.OnPhoto, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Tone.Scrim).padding(horizontal = Space.xs, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ItemSheet(app: TetherApp, d: IgDraft, item: IgItem, photo: Photo?, onDismiss: () -> Unit) {
    var story by remember(item.photoId) { mutableStateOf(false) }
    val frame = if (story) item.story else item.feed
    val position = d.chosen.indexOfFirst { it.photoId == item.photoId }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = Space.l).padding(bottom = Space.xl).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SheetHeader(
                if (position == 0) "Capa do carrossel" else "Foto ${position + 1} do carrossel",
                subtitle = "${item.moment.label} · nota ${item.score.toInt()}",
                onClose = onDismiss,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                JrChip("Feed ${item.feed.shapeLabel}", selected = !story, onClick = { story = false })
                JrChip(if (item.story != null) "Stories 9:16" else "Stories: não cabe", selected = story, enabled = item.story != null, onClick = { story = true })
            }
            FramedPhoto(photo, item.aspect, frame)
            if (frame != null) FrameSliders(item.aspect, frame) { moved -> app.insta.update { it.crop(item.photoId, feed = if (story) null else moved, story = if (story) moved else null) } }
            if (item.reasons.isNotEmpty()) Text(item.reasons.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Tone.Text)
            if (item.warnings.isNotEmpty()) Text(item.warnings.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Tone.Warning)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (position > 0) JrButton("Usar como capa", kind = JrButtonKind.Secondary, icon = Icons.Outlined.Star, onClick = { app.insta.update { it.cover(item.photoId) } })
                IconButton(onClick = { app.insta.update { it.move(item.photoId, -1) } }, enabled = position > 0) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Uma posição antes", tint = Tone.Text)
                }
                IconButton(onClick = { app.insta.update { it.move(item.photoId, 1) } }, enabled = position < d.chosen.size - 1) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Uma posição depois", tint = Tone.Text)
                }
                JrButton("Tirar do post", kind = JrButtonKind.Danger, onClick = { app.insta.remove(item.photoId); onDismiss() })
            }
            Text(
                "Tirar ou pôr uma foto ensina a seleção: com o uso, ela fica mais parecida com o que você escolheria.",
                style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
            )
        }
    }
}

/** The photo with the frame drawn on it and the outside darkened, as it will be cut. */
@Composable
private fun FramedPhoto(photo: Photo?, aspect: Float, frame: CropSpec?) {
    val veil = Tone.Scrim
    val line = Tone.Accent
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.5f, 2.5f)).clip(MaterialTheme.shapes.extraSmall).background(Tone.SurfaceHigh)
            .drawWithContent {
                drawContent()
                if (frame != null) {
                    val l = frame.left * size.width
                    val t = frame.top * size.height
                    val r = frame.right * size.width
                    val b = frame.bottom * size.height
                    drawRect(veil, Offset.Zero, Size(size.width, t))
                    drawRect(veil, Offset(0f, b), Size(size.width, size.height - b))
                    drawRect(veil, Offset(0f, t), Size(l, b - t))
                    drawRect(veil, Offset(r, t), Size(size.width - r, b - t))
                    drawRect(line, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 2.dp.toPx()))
                }
            },
    ) {
        AsyncImage(model = photo?.displayUri, contentDescription = "Foto com o recorte", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** Moves the frame sideways (and up/down when the photo is taller than the frame); the size stays. */
@Composable
private fun FrameSliders(aspect: Float, frame: CropSpec, onMove: (CropSpec) -> Unit) {
    val colors = SliderDefaults.colors(thumbColor = Tone.Accent, activeTrackColor = Tone.Accent, inactiveTrackColor = Tone.SurfaceHigh)
    if (frame.width < 0.995f) {
        Text("Posição na horizontal", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        val cx = (frame.left + frame.right) / 2
        Slider(
            value = cx, onValueChange = { onMove(frame.centredAt(it, (frame.top + frame.bottom) / 2)) },
            valueRange = frame.width / 2..(1f - frame.width / 2).coerceAtLeast(frame.width / 2 + 0.001f), colors = colors,
        )
    }
    if (frame.height < 0.995f) {
        Text("Posição na vertical", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        val cy = (frame.top + frame.bottom) / 2
        Slider(
            value = cy, onValueChange = { onMove(frame.centredAt((frame.left + frame.right) / 2, it)) },
            valueRange = frame.height / 2..(1f - frame.height / 2).coerceAtLeast(frame.height / 2 + 0.001f), colors = colors,
        )
    }
}

// ---- caption --------------------------------------------------------------------------------

@Composable
private fun CaptionCard(app: TetherApp, e: ChurchEvent, d: IgDraft, s: Settings) {
    val context = LocalContext.current
    DeliveryCard {
        Text("Legenda", style = MaterialTheme.typography.titleMedium)
        Text(
            "Feita no celular a partir do evento e do que você preencher abaixo. Linhas sem informação ficam de fora.",
            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
        )
        fun setNotes(n: IgCaption.Notes) = app.insta.update { cur ->
            val next = cur.copy(notes = n)
            if (cur.captionEdited) next else next.copy(caption = suggestion(app, e, next, s, cur.model))
        }
        NoteField("Tema", d.notes.theme) { setNotes(d.notes.copy(theme = it)) }
        NoteField("Versículo", d.notes.verse) { setNotes(d.notes.copy(verse = it)) }
        NoteField("Pregador", d.notes.preacher) { setNotes(d.notes.copy(preacher = it)) }
        NoteField("Louvor", d.notes.worship) { setNotes(d.notes.copy(worship = it)) }
        OutlinedTextField(
            value = d.caption,
            onValueChange = { text -> app.insta.update { it.copy(caption = text.take(2200), captionEdited = true) } },
            label = { Text("Texto do post") },
            supportingText = { Text("${d.caption.length} de 2200 caracteres") },
            minLines = 6,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Tone.Accent, unfocusedBorderColor = Tone.Line),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            JrButton("Outra sugestão", kind = JrButtonKind.Secondary, onClick = {
                app.insta.update { cur -> (cur.model + 1).let { m -> cur.copy(model = m, caption = suggestion(app, e, cur, s, m), captionEdited = false) } }
            })
            JrButton("Copiar", kind = JrButtonKind.Ghost, icon = Icons.Outlined.ContentCopy, onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Legenda", d.caption))
                app.notice("Legenda copiada")
            })
        }
    }
}

@Composable
private fun NoteField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.replace('\n', ' ').take(160)) }, label = { Text(label) }, singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Tone.Accent, unfocusedBorderColor = Tone.Line),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- send -----------------------------------------------------------------------------------

@Composable
private fun SendCard(app: TetherApp, e: ChurchEvent, d: IgDraft) {
    val scope = rememberCoroutineScope()
    val status by app.church.status.collectAsState()
    DeliveryCard {
        Text("Mandar para a mídia", style = MaterialTheme.typography.titleMedium)
        val sent = e.igSentAt
        Text(
            if (sent != null) "Na página da mídia desde ${SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(sent))}. Mandar de novo atualiza as fotos, a ordem e a legenda."
            else "As fotos aparecem no topo da página da igreja, em \"Para o Instagram\", com o look do evento e os recortes de feed e Stories prontos para baixar.",
            style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted,
        )
        if (status is DeliveryStatus.Off) Text("A página só abre com a entrega da igreja no ar.", style = MaterialTheme.typography.bodySmall, color = Tone.Warning)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            JrButton(
                if (sent != null) "Mandar de novo" else "Mandar para a mídia",
                enabled = d.chosen.isNotEmpty(),
                onClick = {
                    scope.launch {
                        app.insta.save(d, System.currentTimeMillis())
                        app.church.repo.log(e.id, null, "Mandou para o Instagram", "${d.chosen.size} fotos", byPhotographer = true)
                        app.church.repo.changed()
                        app.notice("${d.chosen.size} fotos na página da mídia")
                    }
                },
            )
            if (sent != null) JrButton("Tirar da página", kind = JrButtonKind.Danger, onClick = {
                scope.launch {
                    app.insta.unsend(d)
                    app.church.repo.changed()
                }
            })
        }
    }
}

// ---- the church's style ---------------------------------------------------------------------

@Composable
private fun StyleCard(app: TetherApp, e: ChurchEvent) {
    val scope = rememberCoroutineScope()
    var style by remember(e.congregationId) { mutableStateOf(app.insta.style(e.congregationId)) }
    var voice by remember(e.congregationId) { mutableStateOf(app.insta.voice(e.congregationId)) }
    var lessons by remember { mutableIntStateOf(app.insta.weights().lessons) }
    var importing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val done: (com.example.core.insta.IgImported?) -> Unit = { r ->
        importing = null
        style = app.insta.style(e.congregationId)
        voice = app.insta.voice(e.congregationId)
        app.notice(if (r == null) "Não deu para ler esse arquivo." else if (r.photos == 0) "Nenhuma foto de post encontrada." else "Aprendido de ${r.photos} posts.")
    }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = 0 to 0
            done(com.example.core.insta.InstaImport(app, app.insta).fromZip(uri, e.congregationId) { a, b -> importing = a to b })
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            importing = 0 to uris.size
            done(com.example.core.insta.InstaImport(app, app.insta).fromImages(uris, e.congregationId) { a, b -> importing = a to b })
        }
    }
    DeliveryCard {
        Text("Estilo da igreja", style = MaterialTheme.typography.titleMedium)
        if (style.count >= IgStyle.MIN_COUNT) {
            Text(
                "Aprendido de ${style.count} posts de ${e.congregation}. Feed em ${style.feedRatio.let { com.example.core.render.CropSpec(ratio = it.id).shapeLabel }}" +
                    (voice?.credit?.let { ", crédito $it" } ?: "") + ".",
                style = MaterialTheme.typography.bodyMedium, color = Tone.Text,
            )
            voice?.hashtags?.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted) }
        } else {
            Text(
                "Ensine o jeito da igreja: o módulo olha os posts que vocês já fizeram (luz, cor, quantas pessoas, o formato do feed) " +
                    "e as legendas (hashtags e crédito das fotos). Tudo fica no celular.",
                style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted,
            )
            Text(
                "Para o ZIP: no Instagram, Central de contas → Suas informações e permissões → Baixar suas informações → Publicações. Qualidade baixa já serve.",
                style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
            )
        }
        importing?.let { (a, b) ->
            LinearProgressIndicator(progress = { if (b == 0) 0f else a / b.toFloat() }, color = Tone.Accent, trackColor = Tone.SurfaceHigh, modifier = Modifier.fillMaxWidth())
            Text(if (b == 0) "Lendo o arquivo…" else "Aprendendo: $a de $b posts", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        }
        if (importing == null) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            JrButton("Importar o ZIP", kind = JrButtonKind.Secondary, icon = Icons.Outlined.UploadFile, onClick = { zip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) })
            JrButton("Da galeria", kind = JrButtonKind.Ghost, icon = Icons.Outlined.PhotoLibrary, onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        }
        if (style.count > 0 || lessons > 0) Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            if (lessons > 0) Text("Aprendeu com $lessons escolhas suas.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            JrButton("Esquecer", kind = JrButtonKind.Danger, onClick = {
                app.insta.clearStyle(e.congregationId)
                app.insta.forgetChoices()
                style = app.insta.style(e.congregationId)
                voice = app.insta.voice(e.congregationId)
                lessons = 0
            })
        }
    }
}
