package com.example.revela

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.TetherApp
import com.example.core.camera.CameraStatus
import com.example.core.camera.ConnectionState
import com.example.core.camera.ShutterState
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.ui.components.BrandLoader
import com.example.ui.components.ValuePickerSheet
import com.example.ui.components.shutterFlash
import com.example.ui.live.ShutterButton
import com.example.ui.preset.LookImage
import com.example.ui.session.IntervalDialog
import com.example.ui.session.SessionChip
import com.example.ui.session.SessionNameSheet
import com.example.ui.theme.DisplayStyle
import com.example.ui.theme.ReadoutStyle
import com.example.ui.theme.Tone

/**
 * Revela's home. The last frame fills the screen like a print on the light table; the camera reads across
 * the top like a viewfinder; the newest frames run along the bottom as a film strip; the shutter sits
 * under the thumb. Everything else lives in the menu (top left), not in tabs.
 */
@Composable
fun CaptureScreen(
    app: TetherApp,
    settings: Settings,
    onMenu: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenLive: () -> Unit,
) {
    val state by app.camera.state.collectAsState()
    val status by app.camera.status.collectAsState()
    val shutter by app.camera.shutter.collectAsState()
    val interval by app.camera.interval.collectAsState()
    val photos by remember { app.photos }.collectAsState(initial = emptyList())
    val looks by app.presets.presets.collectAsState(initial = emptyList())
    val connected = state is ConnectionState.Connected
    val latest = photos.firstOrNull()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var picker by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var naming by remember { mutableStateOf(false) }
    var showInterval by remember { mutableStateOf(false) }
    val shoot = { app.camera.capture(settings.shutterWait, settings.shutterFocusFirst) }
    val intervalAction = { if (interval.running) app.camera.stopInterval() else showInterval = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The print: the last frame, whole, with the look applied.
        if (latest != null) {
            Box(Modifier.fillMaxSize().shutterFlash(latest.id).clickable { onOpenPhoto(latest.id) }) {
                LookImage(app, latest, looks, Modifier.fillMaxSize(), ContentScale.Fit)
            }
        } else if (connected || photos.isEmpty()) {
            EmptyPrint(connected, onConnect = { app.camera.findAndConnect() }, onOpenGallery = onOpenGallery)
        }

        // Shade at the top and bottom so the readouts and controls read over any photo.
        Box(Modifier.fillMaxWidth().height(if (landscape) 120.dp else 200.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))))
        if (!landscape) {
            Box(Modifier.fillMaxWidth().height(280.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
        } else {
            Box(Modifier.fillMaxHeight().width(260.dp).align(Alignment.CenterEnd)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
        }

        // Top: menu, studio, session and the camera.
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding()
                .padding(start = 8.dp, end = if (landscape) 260.dp else 16.dp, top = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Outlined.Menu, stringResource(R.string.menu_open), onMenu)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.brandName.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.headlineSmall, color = Tone.OnPhoto, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    CameraLine(state, status, settings, onConnect = { app.camera.findAndConnect() })
                }
            }
            SessionChip(settings.sessionName, photos.size, Modifier.padding(start = 56.dp, top = 8.dp).widthIn(max = 400.dp)) { naming = true }
            if (connected && !landscape) {
                Readouts(status, Modifier.padding(start = 8.dp, top = 12.dp)) { title, prop -> picker = title to prop }
            }
        }

        if (landscape) {
            // Lying down: readouts and the shutter in a column on the right, the film strip along the bottom left.
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(248.dp).statusBarsPadding().navigationBarsPadding()
                    .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (connected) Readouts(status, Modifier, columns = true) { title, prop -> picker = title to prop } else Spacer(Modifier)
                ShutterBlock(shutter, connected, shoot)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SideAction(Icons.Outlined.PhotoLibrary, photos.size.toString(), false, true, onOpenGallery)
                    SideAction(Icons.Outlined.Timer, if (interval.running) "${interval.done}/${interval.total}" else stringResource(R.string.interval), interval.running, connected, intervalAction)
                    SideAction(Icons.Outlined.Videocam, stringResource(R.string.nav_live), false, connected, onOpenLive)
                }
            }
            FilmStrip(photos, latest?.id, onOpenPhoto, Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(end = 264.dp, bottom = 8.dp))
        } else {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
                FilmStrip(photos, latest?.id, onOpenPhoto, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SideAction(Icons.Outlined.PhotoLibrary, stringResource(R.string.gallery_count, photos.size), false, true, onOpenGallery)
                    ShutterBlock(shutter, connected, shoot)
                    SideAction(
                        if (interval.running) Icons.Outlined.Timer else Icons.Outlined.Videocam,
                        if (interval.running) "${interval.done}/${interval.total}" else stringResource(R.string.nav_live),
                        interval.running, connected,
                        if (interval.running) intervalAction else onOpenLive,
                    )
                }
                if (connected && !interval.running) {
                    TextButton(onClick = intervalAction, modifier = Modifier.align(Alignment.End).padding(end = 12.dp).heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = Tone.OnPhoto, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.interval), color = Tone.OnPhoto, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    picker?.let { (title, prop) ->
        ValuePickerSheet(
            title = title,
            prop = prop,
            options = status.options(prop).ifEmpty { EosValues.defaultValues(prop) },
            current = status.value(prop),
            onPick = { app.camera.setProperty(prop, it); picker = null },
            onDismiss = { picker = null },
        )
    }
    if (naming) {
        SessionNameSheet(app, settings.sessionName, startNew = false, onStarted = { naming = false }, onDismiss = { naming = false })
    }
    if (showInterval) {
        IntervalDialog(onStart = { seconds, shots -> app.camera.startInterval(seconds, shots); showInterval = false }, onDismiss = { showInterval = false })
    }
}

/** One line under the studio: which camera, in what state, and its battery. */
@Composable
private fun CameraLine(state: ConnectionState, status: CameraStatus, settings: Settings, onConnect: () -> Unit) {
    val (color, text) = when (state) {
        is ConnectionState.Connected -> Tone.Accent to state.model
        ConnectionState.Connecting -> Tone.Warning to stringResource(R.string.cam_connecting)
        ConnectionState.WaitingPermission -> Tone.Warning to stringResource(R.string.cam_waiting)
        is ConnectionState.Reconnecting -> Tone.Warning to stringResource(R.string.cam_reconnecting, state.attempt)
        is ConnectionState.Failed -> Tone.Error to stringResource(R.string.cam_failed, state.message)
        ConnectionState.NoCamera -> Tone.TextMuted to stringResource(R.string.cam_none)
    }
    val dot by animateColorAsState(color, label = "camera")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 24.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Tone.OnPhoto.copy(alpha = 0.86f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (state is ConnectionState.Connected && settings.showBattery) {
            status.value(EosProp.BATTERY_POWER)?.let {
                Text("  ·  " + stringResource(R.string.battery, EosValues.batteryLabel(it)), style = MaterialTheme.typography.bodyMedium, color = Tone.OnPhoto.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        if (state is ConnectionState.NoCamera || state is ConnectionState.Failed) {
            TextButton(onClick = onConnect, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(stringResource(R.string.cam_connect), color = Tone.Accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The exposure, as the viewfinder shows it: each value tappable to change it from the phone. */
@Composable
private fun Readouts(status: CameraStatus, modifier: Modifier, columns: Boolean = false, onPick: (String, Int) -> Unit) {
    fun text(prop: Int) = status.value(prop)?.let { EosValues.label(prop, it) } ?: "--"
    val mode = status.value(EosProp.SHOOTING_MODE)
    val items = listOf(
        Triple(stringResource(R.string.shutter_speed), EosProp.SHUTTER_SPEED, mode in setOf(1, 3, 4)),
        Triple(stringResource(R.string.aperture), EosProp.APERTURE, mode in setOf(2, 3, 4)),
        Triple("ISO", EosProp.ISO, true),
        Triple(stringResource(R.string.compensation), EosProp.EXPOSURE_COMPENSATION, mode != 3),
    )
    val cells = @Composable {
        Readout(stringResource(R.string.mode), text(EosProp.SHOOTING_MODE), false) {}
        items.forEach { (title, prop, editable) -> Readout(shortLabel(prop), text(prop), editable) { onPick(title, prop) } }
    }
    if (columns) Column(modifier, horizontalAlignment = Alignment.End) { cells() }
    else Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) { cells() }
}

private fun shortLabel(prop: Int) = when (prop) {
    EosProp.SHUTTER_SPEED -> "Tv"
    EosProp.APERTURE -> "Av"
    EosProp.EXPOSURE_COMPENSATION -> "±EV"
    else -> "ISO"
}

@Composable
private fun Readout(label: String, value: String, editable: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = editable, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tone.OnPhoto.copy(alpha = 0.6f), maxLines = 1)
        Text(value, style = ReadoutStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = if (editable) Tone.OnPhoto else Tone.OnPhoto.copy(alpha = 0.75f), maxLines = 1)
    }
}

/** The newest frames, newest on the left; the one on screen is outlined in the brand colour. */
@Composable
private fun FilmStrip(photos: List<Photo>, current: Long?, onOpen: (Long) -> Unit, modifier: Modifier) {
    if (photos.size < 2) return
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(photos.take(30), key = { it.id }) { p ->
            val on = p.id == current
            Box(
                Modifier
                    .size(width = 60.dp, height = 60.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .border(if (on) 2.dp else 0.dp, if (on) Tone.Accent else Color.Transparent, MaterialTheme.shapes.extraSmall)
                    .background(Tone.SurfaceHigh)
                    .clickable(role = Role.Button) { onOpen(p.id) }
                    .semantics { contentDescription = p.savedBase },
            ) {
                AsyncImage(model = p.lookUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ShutterBlock(shutter: ShutterState, connected: Boolean, onShoot: () -> Unit) {
    val hint = when (shutter) {
        is ShutterState.Focusing -> stringResource(R.string.shot_focusing)
        ShutterState.Fired -> stringResource(R.string.shot_fired)
        is ShutterState.Failed -> stringResource(R.string.shot_failed)
        ShutterState.Idle -> if (connected) null else stringResource(R.string.shot_need_camera)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ShutterButton(shutter, connected, if (connected) 80.dp else 64.dp, Modifier.semantics { contentDescription = "Disparar" }, onShoot)
        Text(
            hint ?: " ", style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (shutter is ShutterState.Failed) Tone.Error else Tone.OnPhoto.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SideAction(icon: ImageVector, label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 72.dp).clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (on) Tone.Accent else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (on) Tone.OnAccent else Tone.OnPhoto.copy(alpha = if (enabled) 1f else 0.4f)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.OnPhoto.copy(alpha = if (enabled) 0.9f else 0.45f), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Tone.OnPhoto) }
}

/** No frame yet: what to do, in the voice of the app, with the one action in the brand colour. */
@Composable
private fun BoxScope.EmptyPrint(connected: Boolean, onConnect: () -> Unit, onOpenGallery: () -> Unit) {
    Column(
        Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        BrandLoader(56.dp, still = true)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(if (connected) R.string.first_photo_title else R.string.connect_title),
            style = DisplayStyle, color = Tone.Text,
        )
        Text(
            stringResource(if (connected) R.string.first_photo_text else R.string.connect_text),
            style = MaterialTheme.typography.bodyLarge, color = Tone.TextMuted, modifier = Modifier.padding(top = 10.dp),
        )
        if (!connected) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.connect_button), style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onOpenGallery, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.open_gallery), color = Tone.TextMuted)
            }
        }
    }
}
