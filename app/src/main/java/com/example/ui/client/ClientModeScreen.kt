package com.example.ui.client

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.Settings
import com.example.ui.theme.Tone
import com.example.ui.theme.WordmarkStyle
import com.example.ui.components.BrandSymbol
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import com.example.core.data.Photo
import com.example.core.preset.Preset
import com.example.ui.preset.LookImage
import com.example.ui.components.Zoomable
import com.example.ui.PhotoActions
import com.example.ui.photos.WhatsAppSheet
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import com.example.ui.preset.PresetPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Iso
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ShutterSpeed

/**
 * Full-screen photos for the client to follow along. No settings, no delete.
 * The photo has its own area; the info line and the icon bar sit below it, never over it.
 * Exit: the Sair button, or one finger held still for 3 s (plus the PIN when one is set).
 */
@Composable
fun ClientModeScreen(app: TetherApp, onExit: () -> Unit, onCalibrate: (Long) -> Unit, onCrop: (Long) -> Unit = {}) {
    val all by remember { app.database.photoDao().observeAll() }.collectAsState(initial = null)
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val s by app.settings.settings.collectAsState(initial = null)
    val settings = s ?: return
    val photos = (all ?: return).filter {
        it.session == settings.sessionKey && (!settings.clientOnlyFavorites || it.favorite) && it.displayUri != null
    }
    var askPin by remember { mutableStateOf(false) }
    var calibrateAsk by remember { mutableStateOf<Long?>(null) }
    var whatsFor by remember { mutableStateOf<Photo?>(null) }
    val pager = rememberPagerState { photos.size }

    com.example.ui.components.ImmersiveMode()
    // Back does nothing here, so a client can't leave by accident.
    BackHandler { }

    val requestExit = { if (settings.clientPin.isEmpty()) onExit() else askPin = true }
    // Calibrar and Recortar are the photographer's: both ask for the PIN when one is set.
    var cropAsk by remember { mutableStateOf<Long?>(null) }
    val guarded = settings.clientCalibratePin && settings.clientPin.isNotEmpty()
    val requestCalibrate = { id: Long -> if (guarded) calibrateAsk = id else onCalibrate(id) }
    val requestCrop = { id: Long -> if (guarded) cropAsk = id else onCrop(id) }
    val holdToExit by rememberUpdatedState(settings.clientExitHold)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!holdToExit) return@awaitEachGesture
                    // Hold-to-exit needs one finger held still for 3 s. A second finger (pinch to zoom),
                    // a drag (swipe or pan) or lifting the finger all cancel it.
                    var still = true
                    val lifted = withTimeoutOrNull(3_000) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.size > 1 ||
                                event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
                            ) still = false
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                    if (lifted == null && still) requestExit()
                }
            },
    ) {
        val current = photos.getOrNull(pager.currentPage)

        // Top: leave on the left, position and brand on the right.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.clientExitButton) {
                TextButton(onClick = requestExit) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sair", color = Muted, style = MaterialTheme.typography.labelLarge)
                    if (settings.clientPin.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.Lock, contentDescription = "Com PIN", tint = Muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (photos.isNotEmpty()) {
                Text("${pager.currentPage + 1} / ${photos.size}", style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.width(12.dp))
            }
            if (settings.clientShowBrand) {
                Row(Modifier.padding(end = 12.dp).alpha(0.8f), verticalAlignment = Alignment.CenterVertically) {
                    BrandSymbol(18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Jr_Tech_OFC", style = WordmarkStyle.copy(fontSize = 13.sp), color = Color.White)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (photos.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("As fotos aparecem aqui assim que forem feitas.", color = Muted)
                }
            } else {
                ClientPager(app, pager, photos, presets, settings)
            }
        }

        if (current != null) {
            if (settings.clientShowInfo) PhotoInfo(current)
            if (settings.clientPresetsEnabled && presets.isNotEmpty()) {
                PresetPicker(
                    app = app,
                    presets = presets,
                    selectedId = current.presetId,
                    sourceUri = current.displayUri,
                    onPick = { picked -> scope.launch { app.looks.setLook(current, picked?.id, null) } },
                    onDark = true,
                )
            }
            // Big icons with a short name under each: readable at a glance, even at arm's length.
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f)).navigationBarsPadding().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (settings.clientPicksEnabled) {
                    ClientAction(
                        if (current.clientPick) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        if (current.clientPick) "Escolhida" else "Escolher",
                        tint = if (current.clientPick) Tone.Accent else Color.White,
                    ) { scope.launch { app.database.photoDao().setClientPick(current.id, !current.clientPick) } }
                }
                if (settings.clientWhatsApp) {
                    ClientAction(Icons.AutoMirrored.Outlined.Send, "WhatsApp", tint = Tone.Accent) { whatsFor = current }
                }
                if (settings.clientCropEnabled) {
                    ClientAction(Icons.Outlined.Crop, "Recortar", locked = guarded) { requestCrop(current.id) }
                }
                if (settings.clientPresetsEnabled && presets.isNotEmpty()) {
                    ClientAction(Icons.Outlined.Tune, "Calibrar", locked = guarded) { requestCalibrate(current.id) }
                }
            }
        }
    }

    if (askPin) {
        PinDialog(settings.clientPin, "PIN para sair", "Sair", onOk = { askPin = false; onExit() }, onCancel = { askPin = false })
    }
    whatsFor?.let { photo ->
        WhatsAppSheet(app, remember { PhotoActions(app, app.store, app.export, app.looks) }, settings, listOf(photo), forClient = true, onDismiss = { whatsFor = null })
    }
    cropAsk?.let { id ->
        PinDialog(settings.clientPin, "PIN para recortar", "Recortar", onOk = { cropAsk = null; onCrop(id) }, onCancel = { cropAsk = null })
    }
    calibrateAsk?.let { id ->
        PinDialog(settings.clientPin, "PIN para calibrar", "Calibrar", onOk = { calibrateAsk = null; onCalibrate(id) }, onCancel = { calibrateAsk = null })
    }
}

private val Muted = Color(0xFFBDBDBD)

/** File name and exposure, each with its own icon, on a line of its own under the photo. */
@Composable
private fun PhotoInfo(photo: Photo) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoItem(Icons.Outlined.Image, photo.savedBase)
        photo.shutter?.let { InfoItem(Icons.Outlined.ShutterSpeed, it) }
        photo.aperture?.let { InfoItem(Icons.Outlined.Camera, it) }
        photo.iso?.let { InfoItem(Icons.Outlined.Iso, it) }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1)
    }
}

@Composable
private fun ClientAction(icon: ImageVector, label: String, tint: Color = Color.White, locked: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).widthIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            if (locked) {
                Icon(
                    Icons.Outlined.Lock, contentDescription = null, tint = Muted,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).size(12.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
    }
}

@Composable
private fun ClientPager(
    app: TetherApp,
    pager: PagerState,
    entries: List<Photo>,
    presets: List<Preset>,
    settings: Settings,
) {
    // While the client looks closely at a photo, nothing moves it away.
    var zoomed by remember { mutableStateOf(false) }
    val isZoomed by rememberUpdatedState(zoomed)
    // New photo: jump to it (the list is newest first).
    LaunchedEffect(Unit) {
        app.tether.saved.collect {
            delay(300)
            if (!isZoomed) pager.animateScrollToPage(0)
        }
    }
    // Optional slideshow through the session.
    LaunchedEffect(settings.clientSlideSeconds, entries.size) {
        if (settings.clientSlideSeconds <= 0 || entries.size < 2) return@LaunchedEffect
        while (true) {
            delay(settings.clientSlideSeconds * 1000L)
            if (!isZoomed) pager.animateScrollToPage((pager.currentPage + 1) % entries.size)
        }
    }
    val latest by rememberUpdatedState(entries)
    HorizontalPager(
        state = pager,
        key = { com.example.ui.photos.pagerKey(latest, it) },
        userScrollEnabled = !zoomed,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val photo = latest.getOrNull(page) ?: return@HorizontalPager
        Zoomable(key = photo.id, onZoomChanged = { zoomed = it }) {
            LookImage(app, photo, presets, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PinDialog(expected: String, title: String, action: String, onOk: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Tone.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6); wrong = false },
                isError = wrong,
                supportingText = { if (wrong) Text("PIN incorreto") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton(onClick = { if (pin == expected) onOk() else wrong = true }) { Text(action) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}
