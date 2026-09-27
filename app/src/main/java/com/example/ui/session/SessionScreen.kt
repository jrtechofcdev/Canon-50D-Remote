package com.example.ui.session

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.camera.CameraStatus
import com.example.core.camera.ConnectionState
import com.example.core.camera.IntervalState
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.core.tether.Transfer
import com.example.ui.components.FormatTag
import com.example.ui.components.Readout
import com.example.ui.components.SectionLabel
import com.example.ui.components.ValuePickerSheet
import com.example.ui.theme.Tone

@Composable
fun SessionScreen(app: TetherApp, onOpenPhoto: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val state by app.camera.state.collectAsState()
    val status by app.camera.status.collectAsState()
    val interval by app.camera.interval.collectAsState()
    val transfer by app.tether.transfer.collectAsState()
    val received by app.tether.receivedThisSession.collectAsState()
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val photos by remember { app.database.photoDao().observeAll() }.collectAsState(initial = emptyList())
    val connected = state is ConnectionState.Connected

    var picker by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showInterval by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val wide = maxWidth >= 720.dp
        val preview: @Composable (Modifier) -> Unit = { mod ->
            Column(mod) {
                LatestPhoto(photos.firstOrNull(), transfer, onOpenPhoto)
                if (photos.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    RecentStrip(photos.drop(1).take(20), onOpenPhoto)
                }
            }
        }
        val controls: @Composable (Modifier) -> Unit = { mod ->
            Column(mod) {
                ConnectionHeader(state, received, status, onConnect = app.camera::findAndConnect)
                FolderNotice(settings, onOpenSettings)
                Spacer(Modifier.height(16.dp))
                ExposureBar(status, connected) { title, prop -> picker = title to prop }
                Spacer(Modifier.height(20.dp))
                ShutterRow(
                    connected = connected,
                    interval = interval,
                    onShoot = app.camera::capture,
                    onInterval = { if (interval.running) app.camera.stopInterval() else showInterval = true },
                )
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                preview(Modifier.weight(1.4f).verticalScroll(rememberScrollState()))
                controls(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                controls(Modifier)
                Spacer(Modifier.height(20.dp))
                preview(Modifier)
            }
        }
    }

    picker?.let { (title, prop) ->
        val options = status.options(prop).ifEmpty { EosValues.defaultValues(prop) }
        ValuePickerSheet(
            title = title,
            prop = prop,
            options = options,
            current = status.value(prop),
            onPick = {
                app.camera.setProperty(prop, it)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    if (showInterval) {
        IntervalDialog(
            onStart = { seconds, shots ->
                app.camera.startInterval(seconds, shots)
                showInterval = false
            },
            onDismiss = { showInterval = false },
        )
    }
}

@Composable
private fun ConnectionHeader(state: ConnectionState, received: Int, status: CameraStatus, onConnect: () -> Unit) {
    val (title, detail) = when (state) {
        is ConnectionState.Connected -> state.model to buildString {
            append(if (received == 1) "1 foto recebida" else "$received fotos recebidas")
            status.shotsLeft?.let { append(" · cartão: $it disparos") }
        }
        ConnectionState.Connecting -> "Conectando…" to "Abrindo sessão com a câmera"
        ConnectionState.WaitingPermission -> "Permissão USB" to "Toque em Permitir no aviso do Android"
        ConnectionState.NoCamera -> "Câmera não conectada" to "Ligue a 50D e conecte pelo cabo USB OTG"
        is ConnectionState.Failed -> "Falha na conexão" to state.message
    }
    val live = state is ConnectionState.Connected

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Real state indicator: amber only while the tether is live.
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        is ConnectionState.Connected -> Tone.Accent
                        is ConnectionState.Failed -> Tone.Error
                        else -> Tone.Line
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted)
        }
        if (!live && state != ConnectionState.Connecting) {
            OutlinedButton(onClick = onConnect) { Text("Conectar") }
        }
    }
}

@Composable
private fun FolderNotice(settings: Settings, onOpenSettings: () -> Unit) {
    val missing = buildList {
        if (settings.downloadMode.jpg && settings.jpgFolder == null) add("JPG")
        if (settings.downloadMode.raw && settings.rawFolder == null) add("RAW")
    }
    if (missing.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .clickable(onClick = onOpenSettings)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Tone.TextMuted)
        Spacer(Modifier.width(12.dp))
        Text(
            "Pasta ${missing.joinToString(" e ")} não escolhida: as fotos vão para a pasta interna do app. Toque para escolher.",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.TextMuted,
        )
    }
}

@Composable
private fun ExposureBar(status: CameraStatus, connected: Boolean, onPick: (String, Int) -> Unit) {
    fun text(prop: Int) = status.value(prop)?.let { EosValues.label(prop, it) } ?: "--"
    val mode = status.value(EosProp.SHOOTING_MODE)
    // Like on the camera body: Tv is only settable in Tv/M/B, Av only in Av/M/B.
    val tvEditable = connected && mode in setOf(1, 3, 4)
    val avEditable = connected && mode in setOf(2, 3, 4)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .padding(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Readout("MODO", text(EosProp.SHOOTING_MODE), false, {}, Modifier.weight(1f))
            Readout("Tv", text(EosProp.SHUTTER_SPEED), tvEditable, { onPick("Velocidade", EosProp.SHUTTER_SPEED) }, Modifier.weight(1.2f))
            Readout("Av", text(EosProp.APERTURE), avEditable, { onPick("Abertura", EosProp.APERTURE) }, Modifier.weight(1.2f))
        }
        Row(Modifier.fillMaxWidth()) {
            Readout("ISO", text(EosProp.ISO), connected, { onPick("ISO", EosProp.ISO) }, Modifier.weight(1f))
            Readout("±EV", text(EosProp.EXPOSURE_COMPENSATION), connected && mode != 3, { onPick("Compensação", EosProp.EXPOSURE_COMPENSATION) }, Modifier.weight(1.2f))
            Readout("WB", text(EosProp.WHITE_BALANCE), connected, { onPick("Balanço de branco", EosProp.WHITE_BALANCE) }, Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun ShutterRow(connected: Boolean, interval: IntervalState, onShoot: () -> Unit, onInterval: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onInterval, enabled = connected, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (interval.running) "Parar ${interval.done}/${interval.total}" else "Intervalômetro")
        }
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(3.dp, if (connected) Tone.Accent else Tone.Line, CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(if (connected) Tone.Accent else Tone.SurfaceHigh)
                .clickable(enabled = connected, onClick = onShoot)
                .semantics { contentDescription = "Disparar" },
        )
        Spacer(Modifier.width(20.dp))
        // Keeps the shutter visually centred between the two sides.
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LatestPhoto(photo: Photo?, transfer: Transfer?, onOpen: (Long) -> Unit) {
    SectionLabel("Última foto")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(MaterialTheme.shapes.large)
            .background(Tone.Surface)
            .clickable(enabled = photo != null) { photo?.let { onOpen(it.id) } },
    ) {
        Crossfade(targetState = photo?.displayUri, label = "latest") { uri ->
            if (uri != null) {
                AsyncImage(model = uri, contentDescription = photo?.savedBase, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Cada foto feita pelo botão da câmera ou pelo disparador acima aparece aqui e é salva sozinha no celular.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tone.TextMuted,
                    )
                }
            }
        }
        if (photo != null) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Tone.Background.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(photo.savedBase, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    listOfNotNull(photo.shutter, photo.aperture, photo.iso).joinToString("  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Tone.TextMuted,
                )
            }
            FormatTag(photo.rawUri != null, photo.jpgUri != null, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
    if (transfer != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Recebendo ${transfer.fileName}" + if (transfer.queued > 0) " · mais ${transfer.queued} na fila" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Tone.TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { transfer.progress },
            modifier = Modifier.fillMaxWidth(),
            color = Tone.Accent,
            trackColor = Tone.SurfaceHigh,
        )
    }
}

@Composable
private fun RecentStrip(photos: List<Photo>, onOpen: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = photo.displayUri,
                contentDescription = photo.savedBase,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 84.dp, height = 56.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Tone.Surface)
                    .clickable { onOpen(photo.id) },
            )
        }
    }
}

@Composable
private fun IntervalDialog(onStart: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var seconds by remember { mutableStateOf("10") }
    var shots by remember { mutableStateOf("30") }
    val s = seconds.toIntOrNull()?.takeIf { it in 1..3600 }
    val n = shots.toIntOrNull()?.takeIf { it in 1..9999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        title = { Text("Intervalômetro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = seconds, onValueChange = { seconds = it.filter(Char::isDigit) },
                    label = { Text("Intervalo (segundos)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = shots, onValueChange = { shots = it.filter(Char::isDigit) },
                    label = { Text("Quantidade de fotos") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (s != null && n != null) {
                    val total = s.toLong() * (n - 1)
                    Text("Duração: ${total / 60} min ${total % 60} s", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(s!!, n!!) },
                enabled = s != null && n != null,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Iniciar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
