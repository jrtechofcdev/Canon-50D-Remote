package com.example.ui.church

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.church.ChurchAudit
import com.example.core.church.ChurchEvent
import com.example.core.church.ChurchMember
import com.example.core.church.ChurchPerm
import com.example.core.church.ChurchTheme
import com.example.core.church.MemberStatus
import com.example.core.church.PhotoRule
import com.example.core.data.Photo
import com.example.ui.components.JrButton
import com.example.ui.components.JrButtonKind
import com.example.ui.components.JrChip
import com.example.ui.components.SheetHeader
import com.example.ui.components.StatusMark
import com.example.ui.delivery.DeliveryCard
import com.example.ui.theme.Space
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

// ---- photos of the event --------------------------------------------------------------------

/** The event's photos: tap hides one from everybody (or shows it again); "Adicionar" brings older ones in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventPhotosCard(app: TetherApp, e: ChurchEvent) {
    val scope = rememberCoroutineScope()
    val inEvent by app.church.repo.dao.observePhotos(e.id).collectAsState(initial = emptyList())
    val all by app.database.photoDao().observeAll().collectAsState(initial = emptyList())
    val byId = remember(all) { all.associateBy { it.id } }
    var adding by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    DeliveryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fotos do evento", style = MaterialTheme.typography.titleMedium)
                val hidden = inEvent.count { it.hidden }
                Text(
                    "${inEvent.size} ${if (inEvent.size == 1) "foto" else "fotos"}" + if (hidden > 0) " · $hidden ocultas para todos" else "",
                    style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                )
            }
            JrButton("Adicionar", onClick = { adding = true }, kind = JrButtonKind.Secondary, icon = Icons.Outlined.AddPhotoAlternate)
        }
        if (inEvent.isEmpty()) {
            Text("Cada foto nova da câmera entra aqui sozinha enquanto o evento está aberto.", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
        } else {
            Text("Toque numa foto para ocultar de todos ou mostrar de novo.", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
            val shown = if (showAll) inEvent else inEvent.take(24)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { ep ->
                    key(ep.photoId) {
                        Thumb(byId[ep.photoId], dimmed = ep.hidden, badge = if (ep.hidden) Icons.Outlined.VisibilityOff else null) {
                            scope.launch { app.church.repo.hideForAll(e.id, listOf(ep.photoId), !ep.hidden) }
                        }
                    }
                }
            }
            if (inEvent.size > 24 && !showAll) TextButton(onClick = { showAll = true }) { Text("Ver todas as ${inEvent.size}") }
        }
    }
    if (adding) PickPhotosSheet(
        title = "Adicionar ao evento",
        subtitle = "Fotos que ainda não estão no evento",
        photos = all.filter { p -> inEvent.none { it.photoId == p.id } && p.displayUri != null }.take(120),
        onDismiss = { adding = false },
        onPick = { ids -> scope.launch { ids.forEach { app.church.repo.addPhoto(e.id, it) } } },
    )
}

@Composable
internal fun Thumb(photo: Photo?, size: Dp = 72.dp, dimmed: Boolean = false, selected: Boolean = false, badge: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.extraSmall).background(Tone.Surface)
            .then(if (selected) Modifier.border(2.dp, Tone.Accent, MaterialTheme.shapes.extraSmall) else Modifier)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo?.displayUri, contentDescription = photo?.savedBase, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (dimmed) 0.3f else 1f),
        )
        badge?.let {
            Box(Modifier.align(Alignment.Center).size(28.dp).clip(CircleShape).background(Tone.Background.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Icon(it, null, tint = Tone.Text, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PickPhotosSheet(title: String, subtitle: String, photos: List<Photo>, onDismiss: () -> Unit, onPick: (List<Long>) -> Unit) {
    val chosen = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = Space.l).navigationBarsPadding()) {
            SheetHeader(title, subtitle = subtitle, onClose = onDismiss)
            if (photos.isEmpty()) Text("Nenhuma foto para adicionar.", color = Tone.TextMuted, modifier = Modifier.padding(vertical = Space.l))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    photos.forEach { p ->
                        key(p.id) { Thumb(p, size = 88.dp, selected = p.id in chosen) { if (p.id in chosen) chosen.remove(p.id) else chosen.add(p.id) } }
                    }
                }
            }
            JrButton(
                if (chosen.isEmpty()) "Escolha as fotos" else "Adicionar ${chosen.size}",
                onClick = { onPick(chosen.toList()); onDismiss() },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.m),
            )
        }
    }
}

// ---- people ---------------------------------------------------------------------------------

/** The four permissions, in the order the media page shows them. */
internal val PERMS = listOf(
    ChurchPerm.VIEW to "Ver",
    ChurchPerm.DOWNLOAD_ORIGINAL to "Original",
    ChurchPerm.DOWNLOAD_READY to "Pronta",
    ChurchPerm.DOWNLOAD_RAW to "RAW",
)

/** What a newcomer may do. Changing it doesn't touch who is already inside, until "Aplicar a todos". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DefaultPermsCard(app: TetherApp, e: ChurchEvent) {
    val scope = rememberCoroutineScope()
    val members by app.church.repo.dao.observeMembers(e.id).collectAsState(initial = emptyList())
    val different = members.count { it.status != MemberStatus.BLOCKED && it.perms != e.defaultPerms }
    DeliveryCard {
        Text("Quem entrar pode", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            PERMS.forEach { (flag, label) ->
                val on = ChurchPerm.has(e.defaultPerms, flag)
                JrChip(label, on, onClick = { scope.launch { app.church.repo.setDefaultPerm(e.id, flag, !on) } })
            }
        }
        Text(
            when {
                !ChurchPerm.has(e.defaultPerms, ChurchPerm.VIEW) -> "Quem entrar fica esperando você liberar, sem ver as fotos."
                e.defaultPerms and ChurchPerm.DOWNLOAD_ANY == 0 -> "Quem entrar só vê, com o nome na foto. Libere o download pessoa por pessoa em Pessoas."
                else -> "Vale para quem entrar daqui para frente. Quem já entrou continua como está."
            },
            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
        )
        if (different > 0) JrButton(
            "Aplicar a quem já entrou ($different)",
            kind = JrButtonKind.Secondary,
            onClick = { scope.launch { val n = app.church.repo.applyDefaultToAll(e.id); app.notice("$n ${if (n == 1) "pessoa atualizada" else "pessoas atualizadas"}") } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Who came in, with what each one may do. The role they typed gives no power: these switches do. */
@Composable
internal fun PeopleCard(app: TetherApp, e: ChurchEvent) {
    val members by app.church.repo.dao.observeMembers(e.id).collectAsState(initial = emptyList())
    var photosOf by remember { mutableStateOf<ChurchMember?>(null) }
    DeliveryCard {
        Text("Pessoas", style = MaterialTheme.typography.titleMedium)
        val now = System.currentTimeMillis()
        val here = members.count { it.isActive && now - it.lastSeenAt < 2 * 60_000 }
        Text(
            if (members.isEmpty()) "Ninguém entrou ainda. Quem entrar aparece aqui com nome e cargo."
            else "${members.size} ${if (members.size == 1) "pessoa" else "pessoas"} · $here na página agora",
            style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
        )
        members.forEach { m -> key(m.id) { MemberRow(app, m, now, onPhotos = { photosOf = m }) } }
    }
    photosOf?.let { m -> MemberPhotosSheet(app, e, m, onDismiss = { photosOf = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberRow(app: TetherApp, m: ChurchMember, now: Long, onPhotos: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repo = app.church.repo
    var kicking by remember { mutableStateOf(false) }
    // Neutral: mint stays for the one main action of the screen.
    val quiet = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Tone.Text)
    Column(Modifier.fillMaxWidth().padding(top = Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val live = m.isActive && now - m.lastSeenAt < 2 * 60_000
            StatusMark(
                when {
                    m.status == MemberStatus.BLOCKED -> Tone.Error
                    !m.isActive -> Tone.TextMuted
                    live -> Tone.Accent
                    else -> Tone.Line
                },
                height = 14.dp,
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(m.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (m.status) {
                        MemberStatus.BLOCKED -> "Bloqueado"
                        MemberStatus.KICKED -> "Fora (pode entrar de novo com o código)"
                        else -> if (live) "Na página agora" else "Visto às ${clock(m.lastSeenAt.takeIf { it > 0 } ?: m.joinedAt)}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            PERMS.forEach { (flag, label) ->
                val on = ChurchPerm.has(m.perms, flag)
                JrChip(label, on, onClick = { scope.launch { repo.setPerm(m.id, flag, !on) } }, enabled = m.status != MemberStatus.BLOCKED)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            TextButton(onClick = onPhotos, colors = quiet) {
                Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Fotos")
            }
            if (m.status == MemberStatus.BLOCKED) {
                TextButton(onClick = { scope.launch { repo.unblock(m.id) } }, colors = quiet) { Text("Desbloquear") }
            } else {
                TextButton(onClick = { scope.launch { repo.block(m.id) } }, colors = quiet) {
                    Icon(Icons.Outlined.Block, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bloquear")
                }
                if (m.isActive) TextButton(onClick = { kicking = true }) {
                    Icon(Icons.Outlined.Logout, null, modifier = Modifier.size(18.dp), tint = Tone.Error)
                    Spacer(Modifier.width(6.dp))
                    Text("Expulsar", color = Tone.Error)
                }
            }
        }
    }
    if (kicking) AlertDialog(
        onDismissRequest = { kicking = false },
        title = { Text("Expulsar ${m.name}?") },
        text = { Text("A página dessa pessoa volta para a entrada agora. Para não deixar entrar de novo, use Bloquear.") },
        confirmButton = { TextButton(onClick = { kicking = false; scope.launch { repo.kick(m.id) } }) { Text("Expulsar", color = Tone.Error) } },
        dismissButton = { TextButton(onClick = { kicking = false }) { Text("Cancelar") } },
    )
}

/** For one person: event photos hidden from them, and extra photos only they see. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MemberPhotosSheet(app: TetherApp, e: ChurchEvent, m: ChurchMember, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repo = app.church.repo
    val rules by repo.dao.observeRules(m.id).collectAsState(initial = emptyList())
    val inEvent by repo.dao.observePhotos(e.id).collectAsState(initial = emptyList())
    val all by app.database.photoDao().observeAll().collectAsState(initial = emptyList())
    val byId = remember(all) { all.associateBy { it.id } }
    val hidden = rules.filter { it.rule == PhotoRule.HIDDEN }.map { it.photoId }.toSet()
    val extras = rules.filter { it.rule == PhotoRule.EXTRA }.map { it.photoId }.toSet()
    var tab by remember { mutableStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = Space.l).navigationBarsPadding()) {
            SheetHeader("Fotos de ${m.name}", subtitle = m.label, onClose = onDismiss)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                JrChip("Do evento", tab == 0, onClick = { tab = 0 }, count = inEvent.size - hidden.size)
                JrChip("Extras só para ${m.name}", tab == 1, onClick = { tab = 1 }, count = extras.size, icon = Icons.Outlined.Star)
            }
            Text(
                if (tab == 0) "Toque para ocultar uma foto só para ${m.name}. Vale mesmo que o cargo seja de liderança."
                else "Toque para dar uma foto que não está no evento só para ${m.name}.",
                style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.padding(vertical = Space.s),
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = Space.l)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tab == 0) {
                        inEvent.forEach { ep ->
                            val off = ep.photoId in hidden
                            key(ep.photoId) {
                                Thumb(byId[ep.photoId], size = 88.dp, dimmed = off || ep.hidden, badge = if (off) Icons.Outlined.VisibilityOff else null) {
                                    scope.launch { repo.setRule(m.id, ep.photoId, if (off) null else PhotoRule.HIDDEN) }
                                }
                            }
                        }
                    } else {
                        all.filter { p -> inEvent.none { it.photoId == p.id } && p.displayUri != null }.take(120).forEach { p ->
                            val on = p.id in extras
                            key(p.id) {
                                Thumb(p, size = 88.dp, selected = on, badge = if (on) Icons.Outlined.Star else null) {
                                    scope.launch { repo.setRule(m.id, p.id, if (on) null else PhotoRule.EXTRA) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- theme ----------------------------------------------------------------------------------

/** Colours, line and logo of the media page for this event; saved to the congregation too when asked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeCard(app: TetherApp, e: ChurchEvent) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = app.church.repo
    var motto by remember(e.id) { mutableStateOf(e.motto.orEmpty()) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch { repo.updateEvent(e.copy(logoUri = uri.toString())) }
        }
    }
    DeliveryCard {
        Text("Tema da página", style = MaterialTheme.typography.titleMedium)
        Text("Cores, frase e logo que a mídia vê. Vêm da congregação e podem mudar só neste evento.", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
        Text("Cor principal", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        Swatches(e.accent) { c -> scope.launch { repo.updateEvent(e.copy(accent = c)) } }
        Text("Cor de destaque", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        Swatches(e.secondary) { c -> scope.launch { repo.updateEvent(e.copy(secondary = c)) } }
        OutlinedTextField(
            value = motto,
            onValueChange = { motto = it.take(80) },
            label = { Text("Frase (tema, versículo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (motto != e.motto.orEmpty()) JrButton("Salvar frase", onClick = { scope.launch { repo.updateEvent(e.copy(motto = motto.trim().ifBlank { null })) } })
            JrButton(if (e.logoUri == null) "Escolher logo" else "Trocar logo", onClick = { pickLogo.launch(arrayOf("image/*")) }, kind = JrButtonKind.Secondary, icon = Icons.Outlined.Image)
            if (e.logoUri != null) JrButton("Tirar logo", onClick = { scope.launch { repo.updateEvent(e.copy(logoUri = null)) } }, kind = JrButtonKind.Ghost)
            e.congregationId?.let { cid ->
                JrButton("Usar na congregação", kind = JrButtonKind.Ghost, onClick = {
                    scope.launch {
                        repo.dao.congregation(cid)?.let { c ->
                            repo.saveCongregation(c.copy(accent = e.accent, secondary = e.secondary, motto = e.motto, logoUri = e.logoUri, presetId = e.presetId, adjust = e.adjust))
                            app.notice("Os próximos eventos de ${c.name} já começam com este tema.")
                        }
                    }
                })
            }
        }
    }
}

/** Colours that read on the dark page, plus the brand's own. */
internal val SWATCHES = listOf(
    ChurchTheme.ACCENT, ChurchTheme.SECONDARY, "#F2B544", "#FF8A5B", "#FF5C7A", "#C792EA", "#7FD1FF", "#9BE15D", "#FFFFFF", "#E8D5B0",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Swatches(current: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SWATCHES.forEach { hex ->
            val on = hex.equals(current, ignoreCase = true)
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(if (on) 3.dp else 1.dp, if (on) Tone.Text else Tone.Line, CircleShape)
                    .clickable { onPick(hex) },
            )
        }
    }
}

// ---- log ------------------------------------------------------------------------------------

/** Everything that happened, by name and role: entries, downloads, refusals and the photographer's changes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogCard(app: TetherApp, e: ChurchEvent) {
    val context = LocalContext.current
    val log by app.church.repo.dao.observeLog(e.id, 500).collectAsState(initial = emptyList())
    var who by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    DeliveryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Log do evento", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { share(context, e, log) }, enabled = log.isNotEmpty()) {
                Icon(Icons.Outlined.IosShare, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar")
            }
        }
        val people = log.map { it.who }.filter { !it.startsWith("Fotógrafo") }.distinct()
        if (people.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            JrChip("Todos", who == null, onClick = { who = null })
            people.forEach { p -> JrChip(p, who == p, onClick = { who = if (who == p) null else p }) }
        }
        val lines = log.filter { who == null || it.who == who || it.who.endsWith("→ $who") }
        if (lines.isEmpty()) Text("Nada ainda.", style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
        (if (showAll) lines else lines.take(25)).forEach { LogLine(it) }
        if (lines.size > 25 && !showAll) TextButton(onClick = { showAll = true }) { Text("Ver as ${lines.size} linhas") }
    }
}

@Composable
private fun LogLine(a: ChurchAudit) {
    Row(Modifier.fillMaxWidth()) {
        Text(clock(a.at), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = Tone.TextMuted, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text("${a.who}: ${a.action}", style = MaterialTheme.typography.bodySmall, color = if (a.action.startsWith("Tentou")) Tone.Warning else Tone.Text)
            a.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun share(context: android.content.Context, e: ChurchEvent, log: List<ChurchAudit>) {
    val text = buildString {
        appendLine("${e.name}${if (e.congregation.isNotBlank()) " · ${e.congregation}" else ""} · ${day(e.date)}")
        log.sortedBy { it.at }.forEach { a ->
            appendLine("${day(a.at)} ${clock(a.at)}  ${a.who}  ${a.action}${a.detail?.let { " ($it)" } ?: ""}${a.ip?.let { "  [$it]" } ?: ""}")
        }
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Log · ${e.name}").putExtra(Intent.EXTRA_TEXT, text), "Exportar o log")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
