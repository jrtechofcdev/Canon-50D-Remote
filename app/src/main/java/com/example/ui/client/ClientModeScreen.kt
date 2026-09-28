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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import com.example.ui.preset.PresetStrip

/**
 * Full-screen photos for the client to follow along. No bars, no settings, no delete.
 * Exit: hold a finger on the screen for 3 s (plus the PIN when one is set).
 */
@Composable
fun ClientModeScreen(app: TetherApp, onExit: () -> Unit, onCalibrate: (Long) -> Unit) {
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
    val pager = rememberPagerState { photos.size }

    ImmersiveMode()
    // Back does nothing here, so a client can't leave by accident.
    BackHandler { }

    val requestExit = { if (settings.clientPin.isEmpty()) onExit() else askPin = true }
    val requestCalibrate = { id: Long ->
        if (settings.clientCalibratePin && settings.clientPin.isNotEmpty()) calibrateAsk = id else onCalibrate(id)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Exit needs one finger held still for 3 s. A second finger (pinch to zoom), a drag
                    // (swipe or pan) or lifting the finger all cancel it.
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
        if (photos.isEmpty()) {
            Text(
                "As fotos aparecem aqui assim que forem feitas.",
                color = Color(0xFF8A8A8A),
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else {
            val showStrip = settings.clientPresetsEnabled && presets.isNotEmpty()
            ClientPager(app, pager, photos, presets, settings, infoAbove = showStrip)
            val current = photos.getOrNull(pager.currentPage)
            if (showStrip && current != null) {
                // Discreet strip: the client picks the look; Calibrar is for the photographer.
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                        .navigationBarsPadding().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        PresetStrip(presets, current.presetId) { picked -> scope.launch { app.looks.setLook(current, picked?.id, null) } }
                    }
                    IconButton(onClick = { requestCalibrate(current.id) }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Calibrar", tint = Color(0xFFBDBDBD))
                    }
                }
            }
        }
        if (settings.clientShowBrand) {
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).alpha(0.8f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandSymbol(20.dp)
                Spacer(Modifier.width(8.dp))
                Text("Jr_Tech_OFC", style = WordmarkStyle.copy(fontSize = 14.sp), color = Color.White)
            }
        }
    }

    if (askPin) {
        PinDialog(settings.clientPin, "PIN para sair", "Sair", onOk = { askPin = false; onExit() }, onCancel = { askPin = false })
    }
    calibrateAsk?.let { id ->
        PinDialog(settings.clientPin, "PIN para calibrar", "Calibrar", onOk = { calibrateAsk = null; onCalibrate(id) }, onCancel = { calibrateAsk = null })
    }
}

@Composable
private fun ClientPager(
    app: TetherApp,
    pager: PagerState,
    entries: List<Photo>,
    presets: List<Preset>,
    settings: Settings,
    infoAbove: Boolean,
) {
    val scope = rememberCoroutineScope()
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
    HorizontalPager(
        state = pager,
        key = { entries[it].id },
        userScrollEnabled = !zoomed,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val photo = entries[page]
        Box(Modifier.fillMaxSize()) {
            Zoomable(key = photo.id, onZoomChanged = { zoomed = it }) {
                LookImage(app, photo, presets, Modifier.fillMaxSize())
            }
            if (settings.clientPicksEnabled) {
                // The client's own pick; the photographer sees it in the gallery and the session summary.
                IconButton(
                    onClick = { scope.launch { app.database.photoDao().setClientPick(photo.id, !photo.clientPick) } },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).size(56.dp),
                ) {
                    Icon(
                        if (photo.clientPick) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (photo.clientPick) "Tirar da seleção" else "Escolher esta foto",
                        tint = if (photo.clientPick) Tone.Accent else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            if (settings.clientShowInfo) {
                Text(
                    listOfNotNull(photo.savedBase, photo.shutter, photo.aperture, photo.iso).joinToString("   "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFBDBDBD),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (infoAbove) 64.dp else 12.dp),
                )
            }
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

/** Hides status and navigation bars while the client mode is on screen. */
@Composable
private fun ImmersiveMode() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
