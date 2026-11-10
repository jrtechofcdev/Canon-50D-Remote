package com.example.ui.delivery

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.delivery.DeliveryLook
import com.example.core.preset.forPicking
import com.example.ui.preset.PresetThumb
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/** Where the versions go: a client of the Entrega local, or the Express client of the moment (null id). */
private data class VersionTarget(val sessionId: Long?, val label: String)

/** One look on offer: its key in the selection, its name and what it freezes. */
private data class LookOption(val key: String, val name: String, val look: DeliveryLook, val presetId: Long?)

/**
 * "Versões": the same photo sent more than once, each copy with another preset or edit. The client gets
 * every version as its own file (Ana_IMG_1234_v2_Preto-e-branco.jpg); later edits to the photo don't
 * change a version already sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSheet(app: TetherApp, photo: Photo, onDismiss: () -> Unit, onOpenDelivery: () -> Unit, onOpenExpress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    val sessions by app.delivery.sessions.collectAsState(initial = emptyList())
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    val now = remember { System.currentTimeMillis() }
    val s = settings ?: return

    val targets = buildList {
        if (s.deliveryEnabled) {
            sessions.filter { it.isValid(now) }.sortedByDescending { it.id == s.deliveryActiveSession }
                .forEach { add(VersionTarget(it.id, it.client)) }
        }
        if (s.expressEnabled) add(VersionTarget(null, "Fotografia Express"))
    }
    var target by remember { mutableStateOf<VersionTarget?>(null) }
    LaunchedEffect(targets.size) { if (target == null || target !in targets) target = targets.firstOrNull() }

    val currentName = photo.presetId?.let { id -> presets.firstOrNull { it.id == id }?.name }
    val options = buildList {
        add(LookOption("now", currentName?.let { if (photo.adjust != null) "$it calibrado" else it } ?: if (photo.adjust != null) "Editada" else "Como está", DeliveryLook.of(photo), photo.presetId))
        if (photo.presetId != null || photo.adjust != null) add(LookOption("none", "Sem preset", DeliveryLook(crop = photo.crop), null))
        presets.forPicking().filter { it.id != photo.presetId || photo.adjust != null }.forEach { p ->
            add(LookOption("p${p.id}", p.name, DeliveryLook(presetId = p.id, crop = photo.crop), p.id))
        }
    }
    val chosen = remember { mutableStateListOf<String>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Versões da foto", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cada visual escolhido vira um arquivo separado. O cliente recebe todos, e mudar a edição depois não altera o que já foi.",
                style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            if (targets.isEmpty()) {
                Text("Ligue a Entrega local ou a Fotografia Express para mandar versões.", color = Tone.TextMuted)
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onDismiss(); onOpenDelivery() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abrir Entrega") }
                    TextButton(onClick = { onDismiss(); onOpenExpress() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abrir Express") }
                }
                return@Column
            }
            Text("Para quem", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { t ->
                    FilterChip(
                        selected = t == target,
                        onClick = { target = t },
                        label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(if (t.sessionId == null) Icons.Outlined.Collections else Icons.Outlined.Person, null, Modifier.size(18.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tone.SurfaceHigh, selectedLabelColor = Tone.Text, selectedLeadingIconColor = Tone.Accent),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Visuais", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 6.dp),
            ) {
                items(options, key = { it.key }) { o ->
                    val on = o.key in chosen
                    LookTile(
                        name = o.name,
                        selected = on,
                        order = chosen.indexOf(o.key) + 1,
                        onClick = { if (on) chosen.remove(o.key) else chosen.add(o.key) },
                    ) {
                        when {
                            o.key == "now" -> CurrentLookThumb(app, photo)
                            o.presetId != null -> presets.firstOrNull { it.id == o.presetId }?.let { PresetThumb(app, it, photo.displayUri, Modifier.fillMaxSize()) }
                            else -> Box(Modifier.fillMaxSize().background(Tone.SurfaceHigh), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Block, null, tint = Tone.TextMuted)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val t = target ?: return@Button
                    val picked = chosen.mapNotNull { k -> options.firstOrNull { it.key == k } }
                    scope.launch {
                        val tool = if (t.sessionId == null) app.express else app.delivery
                        val sent = picked.count { tool.sendVersion(t.sessionId, photo.id, it.look, it.name) }
                        Toast.makeText(
                            context,
                            when (sent) {
                                0 -> "Não deu para mandar as versões"
                                1 -> "1 versão na fila de ${t.label}"
                                else -> "$sent versões na fila de ${t.label}"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    }
                },
                enabled = chosen.isNotEmpty() && target != null,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp),
            ) {
                Text(
                    when (chosen.size) {
                        0 -> "Escolha um ou mais visuais"
                        1 -> "Mandar 1 versão"
                        else -> "Mandar ${chosen.size} versões"
                    },
                )
            }
        }
    }
}

/** A look on offer: the picture, its name, and the order it was picked in (it is the version's number). */
@Composable
private fun LookTile(name: String, selected: Boolean, order: Int, onClick: () -> Unit, picture: @Composable () -> Unit) {
    val scale by animateFloatAsState(if (selected) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "pick")
    val ring by animateColorAsState(if (selected) Tone.Accent else Color.Transparent, label = "ring")
    Column(Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(2.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                .border(2.dp, ring, MaterialTheme.shapes.extraSmall).padding(3.dp).clip(MaterialTheme.shapes.extraSmall),
        ) {
            picture()
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Tone.Accent),
                    contentAlignment = Alignment.Center,
                ) { Text("$order", style = MaterialTheme.typography.labelMedium, color = Tone.OnAccent) }
            }
        }
        Text(
            name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) Tone.Text else Tone.TextMuted, modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

@Composable
private fun CurrentLookThumb(app: TetherApp, photo: Photo) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.id, photo.presetId, photo.adjust, photo.crop) {
        bitmap = runCatching { app.looks.renderPhoto(photo, app.looks.settingsFor(photo), com.example.core.render.Looks.THUMB_EDGE) }.getOrNull()
    }
    val shown = bitmap
    if (shown != null) {
        Image(shown.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        coil.compose.AsyncImage(photo.displayUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
