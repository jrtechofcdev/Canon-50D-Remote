package com.example.ui.live

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.camera.LiveState
import com.example.core.data.Settings
import com.example.core.live.LiveFrame
import com.example.core.live.LiveOptions
import com.example.core.live.LiveProcessor
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.core.render.RenderParams
import com.example.ui.components.ImmersiveMode
import com.example.ui.components.RotateButton
import com.example.ui.components.ValuePickerSheet
import com.example.core.camera.ShutterState
import androidx.compose.runtime.State
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Deblur
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.PhotoCamera
import com.example.ui.components.Zoomable
import com.example.ui.theme.BrandGradient
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Framing guides drawn over the live picture. */
enum class LiveGrid(val id: String, val label: String) {
    None("none", "Sem guia"), Thirds("thirds", "Terços"), Golden("golden", "Proporção áurea"), Cross("cross", "Centro"),
    Square("square", "Quadrado 1:1"), Portrait45("ig45", "Instagram 4:5"), Story("story", "Stories 9:16"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Thirds
    }
}

/**
 * External monitor for the 50D: the live picture on the phone, optionally with a preset applied in
 * real time (like a colour profile), grids and format guides, zebra, focus peaking and histogram.
 * Tap to place the focus point, camera zoom 1/5/10×, focus nudges, depth-of-field preview, exposure
 * and shutter. Standing up the controls sit under the picture; lying down they move to the sides so
 * the picture gets the whole height.
 */
@Composable
fun LiveViewScreen(
    app: TetherApp,
    onBack: () -> Unit,
    /** Where frames come from; the camera normally, a fixed picture in screenshot tests. */
    frames: kotlinx.coroutines.flow.Flow<ByteArray> = app.camera.liveFrames,
) {
    val activity = LocalActivity.current
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val connection by app.camera.state.collectAsState()
    val live by app.camera.live.collectAsState()
    val status by app.camera.status.collectAsState()
    val shutter by app.camera.shutter.collectAsState()
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    // Held in a state object read only by the picture, so a new frame redraws the picture and not the controls.
    val frame = remember { mutableStateOf<LiveFrame?>(null) }
    var controls by remember { mutableStateOf(true) }
    var showOptions by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val focusMark = remember { mutableStateOf<Offset?>(null) }
    val processor = remember { LiveProcessor(app.engine) }

    // The look on the picture: the session's, a chosen preset, or none.
    val lookId = if (settings.livePresetId == 0L) settings.sessionPresetId else settings.livePresetId
    val lookParams: RenderParams? = remember(lookId, presets) {
        presets.firstOrNull { it.id == lookId }?.let { RenderParams.from(it.settings) }
    }
    val options = LiveOptions(
        look = lookParams,
        zebra = settings.liveZebra,
        zebraLevel = settings.liveZebraLevel,
        peaking = settings.livePeaking,
        peakingColor = peakingColor(settings.livePeakingColor),
        peakingSensitivity = settings.livePeakingSensitivity,
        histogram = settings.liveHistogram,
    )
    val currentOptions by rememberUpdatedState(options)

    // Start when the screen opens (and after a reconnect or a change of output); stop when it closes.
    val connected = connection is ConnectionState.Connected
    // Started only with the real options: the defaults shown for a moment while they load would start
    // it once and restart it right away, which the 50D answered with no picture at all.
    val loaded by remember { app.settings.settings }.collectAsState(initial = null)
    LaunchedEffect(connected, loaded?.liveOnCamera, loaded?.liveSilentAf, loaded?.liveFps) {
        val real = loaded ?: return@LaunchedEffect
        if (connected) {
            app.camera.stopLiveView()
            app.camera.startLiveView(alsoOnCamera = real.liveOnCamera, silentAf = real.liveSilentAf, fps = real.liveFps)
        }
    }
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            app.camera.stopLiveView()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    ImmersiveMode()
    // Frames: newest only; decoding and the look run off the main thread.
    LaunchedEffect(Unit) {
        frames.conflate().collect { jpeg ->
            val next = withContext(Dispatchers.Default) { runCatching { processor.process(jpeg, currentOptions) }.getOrNull() }
            if (next != null) frame.value = next
        }
    }
    LaunchedEffect(focusMark.value) {
        if (focusMark.value != null) { kotlinx.coroutines.delay(1500); focusMark.value = null }
    }
    BackHandler { onBack() }

    val fire = { app.camera.capture(settings.shutterWait, settings.shutterFocusFirst) }
    val onTapPicture = { px: Float, py: Float, mark: Offset ->
        val cx = if (settings.liveMirror) 1f - px else px
        if (settings.liveTapToFocus) {
            focusMark.value = mark
            app.camera.setLiveFocusPoint(cx, py)
            if (settings.liveTapAf) app.camera.liveAutofocus()
        }
    }
    val zoom = (live as? LiveState.Running)?.zoom ?: 1
    val lookName = presets.firstOrNull { it.id == lookId }?.name
    val status1 = statusLine(live, lookParams != null, lookName)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val picture: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                LivePicture(frame, settings, live, connected, focusMark, onTapPicture)
                if (controls) HistogramOverlay(frame, Modifier.align(Alignment.TopEnd).padding(8.dp).size(width = 112.dp, height = 52.dp))
                if (!controls) {
                    // Clean monitor: a small button brings the controls back, and the shutter stays at hand.
                    IconButton(
                        onClick = { controls = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    ) { Icon(Icons.Outlined.Visibility, contentDescription = "Mostrar controles", tint = Color.White) }
                    ShutterButton(shutter, connected, 60.dp, Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter).padding(16.dp), fire)
                }
            }
        }
        when {
            !controls -> picture(Modifier.fillMaxSize())
            landscape -> Row(Modifier.fillMaxSize()) {
                // Left: what the picture is and how it is exposed.
                Column(
                    Modifier.fillMaxHeight().width(84.dp).background(Panel).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ExposureValues(status) { title, prop -> picker = title to prop }
                    }
                    TopActions(app, settings, vertical = true, onOptions = { showOptions = true }, onHide = { controls = false })
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    picture(Modifier.fillMaxSize())
                    StatusChip(status1, Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                // Right: zoom on top, shutter in the middle, focus under it, all under the thumb.
                Column(
                    Modifier.fillMaxHeight().width(140.dp).background(Panel).padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ZoomPills(zoom, compact = true) { app.camera.setLiveZoom(it) }
                    ShutterButton(shutter, connected, 76.dp, Modifier, fire)
                    ShutterHint(shutter)
                    FocusGrid(onAf = { app.camera.liveAutofocus() }, onDrive = { app.camera.driveLens(it) }, onDof = { app.camera.depthOfFieldPreview(it) })
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
                    Text(status1, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.weight(1f))
                    TopActions(app, settings, vertical = false, onOptions = { showOptions = true }, onHide = { controls = false })
                }
                picture(Modifier.weight(1f).fillMaxWidth())
                Column(
                    Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ExposureValues(status) { title, prop -> picker = title to prop }
                    }
                    FocusPad(onAf = { app.camera.liveAutofocus() }, onDrive = { app.camera.driveLens(it) }, onDof = { app.camera.depthOfFieldPreview(it) })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ZoomPills(zoom, compact = false) { app.camera.setLiveZoom(it) } }
                        ShutterButton(shutter, connected, 72.dp, Modifier, fire)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { ShutterHint(shutter) }
                    }
                }
            }
        }
    }

    if (showOptions) LiveOptionsSheet(app, settings, presets, onDismiss = { showOptions = false })

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
}

private val Panel = Color(0xE6101010)

/** The live picture with its overlays. Reads the frame state itself, so only this part redraws per frame. */
@Composable
private fun LivePicture(
    frameState: State<LiveFrame?>,
    settings: Settings,
    live: LiveState,
    connected: Boolean,
    focusMark: androidx.compose.runtime.MutableState<Offset?>,
    onTap: (Float, Float, Offset) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val f = frameState.value
        if (f != null) {
            val pic = Size(f.picture.width.toFloat(), f.picture.height.toFloat())
            val fit = fitRect(box, pic)
            Zoomable(
                key = "live",
                onTap = { x, y ->
                    // Tap position → point on the picture (the picture is letterboxed in the box).
                    val px = (x * box.width - fit.left) / fit.width
                    val py = (y * box.height - fit.top) / fit.height
                    if (px in 0f..1f && py in 0f..1f) onTap(px, py, Offset(x * box.width, y * box.height))
                },
            ) {
                val flip = Modifier.graphicsLayer { scaleX = if (settings.liveMirror) -1f else 1f }
                Image(f.picture.asImageBitmap(), contentDescription = "Imagem ao vivo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().then(flip))
                f.peaking?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().then(flip)) }
                f.zebra?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().then(flip)) }
                val grid = LiveGrid.of(settings.liveGrid)
                if (grid != LiveGrid.None) Canvas(Modifier.fillMaxSize()) { drawGuides(fitRect(size, pic), grid) }
            }
        } else {
            LiveWaiting(live, connected, Modifier.align(Alignment.Center))
        }
        val accent = Tone.Accent
        focusMark.value?.let { p ->
            Canvas(Modifier.fillMaxSize()) {
                val half = 34.dp.toPx()
                val corner = 12.dp.toPx()
                val w = 2.dp.toPx()
                // Corner brackets, like the camera's AF frame.
                listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                    val cx = p.x + sx * half
                    val cy = p.y + sy * half
                    drawLine(accent, Offset(cx, cy), Offset(cx - sx * corner, cy), w)
                    drawLine(accent, Offset(cx, cy), Offset(cx, cy - sy * corner), w)
                }
            }
        }
    }
}

@Composable
private fun HistogramOverlay(frameState: State<LiveFrame?>, modifier: Modifier) {
    frameState.value?.histogram?.let { Histogram(it, modifier) }
}

@Composable
private fun StatusChip(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun TopActions(app: TetherApp, settings: Settings, vertical: Boolean, onOptions: () -> Unit, onHide: () -> Unit) {
    val items: @Composable () -> Unit = {
        RotateButton(app, settings.screenOrientation, tint = Color.White)
        IconButton(onClick = onOptions) { Icon(Icons.Outlined.Layers, contentDescription = "Preset, guias e ajustes do monitor", tint = Color.White) }
        IconButton(onClick = onHide) { Icon(Icons.Outlined.Fullscreen, contentDescription = "Só a imagem", tint = Color.White) }
    }
    if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { items() } else Row { items() }
}

/** The app's shutter: a ring that spins while the camera focuses, and flashes when it fires or fails. */
@Composable
fun ShutterButton(state: ShutterState, enabled: Boolean, size: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    val ring = when {
        !enabled -> Tone.Line
        state is ShutterState.Failed -> Tone.Error
        state == ShutterState.Fired -> Color.White
        else -> Tone.Accent
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (state is ShutterState.Focusing) {
            CircularProgressIndicator(color = Tone.Accent, strokeWidth = 3.dp, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().clip(CircleShape).border(3.dp, if (state is ShutterState.Focusing) Color.Transparent else ring, CircleShape).padding(7.dp).clip(CircleShape)
                .background(if (enabled) BrandGradient else androidx.compose.ui.graphics.SolidColor(Color.Gray))
                .clickable(enabled = enabled && state !is ShutterState.Focusing, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is ShutterState.Focusing -> Icon(Icons.Outlined.CenterFocusWeak, contentDescription = "Focando", tint = Color.Black.copy(alpha = 0.7f))
                ShutterState.Fired -> Icon(Icons.Outlined.Check, contentDescription = "Disparou", tint = Color.Black.copy(alpha = 0.7f))
                is ShutterState.Failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = "Não disparou", tint = Color.Black.copy(alpha = 0.7f))
                ShutterState.Idle -> Icon(Icons.Outlined.PhotoCamera, contentDescription = "Disparar", tint = Color.Black.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun ShutterHint(state: ShutterState) {
    val text = when (state) {
        is ShutterState.Focusing -> if (state.waitedMs > 0) "Focando… %.1f s".format(state.waitedMs / 1000f) else "Focando…"
        ShutterState.Fired -> "Disparou"
        is ShutterState.Failed -> "Não disparou"
        ShutterState.Idle -> ""
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = if (state is ShutterState.Failed) Tone.Error else Color.White.copy(alpha = 0.8f), maxLines = 1)
}

@Composable
private fun ZoomPills(zoom: Int, compact: Boolean, onZoom: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        listOf(1, 5, 10).forEach { z -> Pill("${z}×", zoom == z, compact) { onZoom(z) } }
    }
}

/**
 * Focus by hand with the lens in AF: far on the left (mountain), near on the right (flower), like the
 * camera's own symbols; AF in the middle; DOF works while held.
 */
@Composable
private fun FocusPad(onAf: () -> Unit, onDrive: (Int) -> Unit, onDof: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        FocusIcon(Icons.Outlined.Landscape, "Longe", -3, onDrive)
        FocusIcon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Um pouco mais longe", -1, onDrive)
        AfButton(onAf)
        FocusIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Um pouco mais perto", 1, onDrive)
        FocusIcon(Icons.Outlined.LocalFlorist, "Perto", 3, onDrive)
        HoldIcon(Icons.Outlined.Deblur, "Profundidade de campo (segure)", onDof)
    }
}

/** The same controls in two short rows, for the side rail when the phone lies down. */
@Composable
private fun FocusGrid(onAf: () -> Unit, onDrive: (Int) -> Unit, onDof: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AfButton(onAf)
            HoldIcon(Icons.Outlined.Deblur, "Profundidade de campo (segure)", onDof)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusIcon(Icons.Outlined.Landscape, "Longe", -3, onDrive, small = true)
            FocusIcon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Um pouco mais longe", -1, onDrive, small = true)
            FocusIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Um pouco mais perto", 1, onDrive, small = true)
            FocusIcon(Icons.Outlined.LocalFlorist, "Perto", 3, onDrive, small = true)
        }
    }
}

@Composable
private fun AfButton(onAf: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Tone.Accent.copy(alpha = 0.2f)).border(1.dp, Tone.Accent, CircleShape).clickable(onClick = onAf),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Focar (AF)", tint = Tone.Accent) }
}

@Composable
private fun FocusIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, step: Int, onDrive: (Int) -> Unit, small: Boolean = false) {
    IconButton(onClick = { onDrive(step) }, modifier = if (small) Modifier.size(32.dp) else Modifier) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(if (small) 20.dp else 24.dp))
    }
}

/** Active only while held, like the camera's depth-of-field preview button. */
@Composable
private fun HoldIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onHold: (Boolean) -> Unit) {
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (held) Tone.Accent else Color.White.copy(alpha = 0.1f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    onHold(true)
                    tryAwaitRelease()
                    held = false
                    onHold(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (held) Tone.OnAccent else Color.White) }
}

private fun statusLine(live: LiveState, hasLook: Boolean, lookName: String?): String = when (live) {
    is LiveState.Running -> listOfNotNull(
        "%.0f fps".format(live.fps),
        "zoom ${live.zoom}×".takeIf { live.zoom > 1 },
        lookName?.takeIf { hasLook }?.let { "look: $it" },
    ).joinToString(" · ")
    LiveState.Starting -> "Ligando…"
    is LiveState.Failed -> "Parado"
    LiveState.Off -> "Desligado"
}

@Composable
private fun LiveWaiting(live: LiveState, connected: Boolean, modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !connected -> Text("Conecte a 50D pelo cabo para ver a imagem ao vivo.", color = Color.White.copy(alpha = 0.8f))
            live is LiveState.Failed -> Text(live.message, color = Color.White.copy(alpha = 0.85f))
            else -> {
                CircularProgressIndicator(color = Tone.Info)
                Text("Ligando o live view da câmera…", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

/** Tv, Av, ISO, ±EV and WB; tap one to change it. Laid out by the caller (row or column). */
@Composable
private fun ExposureValues(status: com.example.core.camera.CameraStatus, onPick: (String, Int) -> Unit) {
    fun text(prop: Int) = status.value(prop)?.let { EosValues.label(prop, it) } ?: "--"
    val mode = status.value(EosProp.SHOOTING_MODE)
    Value("Tv", text(EosProp.SHUTTER_SPEED), mode in setOf(1, 3, 4)) { onPick("Velocidade", EosProp.SHUTTER_SPEED) }
    Value("Av", text(EosProp.APERTURE), mode in setOf(2, 3, 4)) { onPick("Abertura", EosProp.APERTURE) }
    Value("ISO", text(EosProp.ISO), true) { onPick("ISO", EosProp.ISO) }
    Value("±EV", text(EosProp.EXPOSURE_COMPENSATION), mode != 3) { onPick("Compensação", EosProp.EXPOSURE_COMPENSATION) }
    Value("WB", text(EosProp.WHITE_BALANCE), true) { onPick("Balanço de branco", EosProp.WHITE_BALANCE) }
}

@Composable
private fun Value(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Tone.OnAccent else Color.White,
        modifier = Modifier.clip(CircleShape).background(if (selected) Tone.Accent else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick).padding(horizontal = if (compact) 9.dp else 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Histogram(bins: List<IntArray>, modifier: Modifier) {
    val colors = listOf(Color(0x66FFFFFF), Color(0xFFE5484D), Color(0xFF46A758), Color(0xFF3E63DD))
    Canvas(modifier.clip(MaterialTheme.shapes.extraSmall).background(Color.Black.copy(alpha = 0.5f))) {
        val max = bins.drop(1).maxOf { it.max() }.coerceAtLeast(1)
        bins.forEachIndexed { c, b ->
            if (c == 0) return@forEachIndexed
            // One bar per bin across the whole box, however many bins the analyser returns.
            val bw = size.width / b.size.coerceAtLeast(1)
            b.forEachIndexed { i, v ->
                val bh = size.height * v / max
                drawRect(colors[c], Offset(i * bw, size.height - bh), Size(bw, bh), blendMode = BlendMode.Plus)
            }
        }
    }
}

private fun fitRect(box: Size, pic: Size): Rect {
    if (pic.width <= 0f || pic.height <= 0f) return Rect(Offset.Zero, box)
    val s = min(box.width / pic.width, box.height / pic.height)
    val w = pic.width * s
    val h = pic.height * s
    return Rect(Offset((box.width - w) / 2, (box.height - h) / 2), Size(w, h))
}

private fun DrawScope.drawGuides(r: Rect, grid: LiveGrid) {
    val c = Color.White.copy(alpha = 0.55f)
    val stroke = 1.dp.toPx()
    fun v(f: Float) = drawLine(c, Offset(r.left + r.width * f, r.top), Offset(r.left + r.width * f, r.bottom), stroke)
    fun h(f: Float) = drawLine(c, Offset(r.left, r.top + r.height * f), Offset(r.right, r.top + r.height * f), stroke)
    fun frame(aspect: Float) {
        // Centred frame of that shape; outside is dimmed so the final crop is easy to judge.
        val fw: Float
        val fh: Float
        if (r.width / r.height > aspect) { fh = r.height; fw = fh * aspect } else { fw = r.width; fh = fw / aspect }
        val left = r.left + (r.width - fw) / 2
        val top = r.top + (r.height - fh) / 2
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, r.topLeft, Size(left - r.left, r.height))
        drawRect(dim, Offset(left + fw, r.top), Size(r.right - left - fw, r.height))
        drawRect(dim, r.topLeft, Size(r.width, top - r.top))
        drawRect(dim, Offset(r.left, top + fh), Size(r.width, r.bottom - top - fh))
        drawRect(Color.White, Offset(left, top), Size(fw, fh), style = Stroke(stroke * 1.5f))
    }
    when (grid) {
        LiveGrid.None -> Unit
        LiveGrid.Thirds -> listOf(1f / 3, 2f / 3).forEach { v(it); h(it) }
        LiveGrid.Golden -> listOf(0.382f, 0.618f).forEach { v(it); h(it) }
        LiveGrid.Cross -> {
            val len = 24.dp.toPx()
            drawLine(c, Offset(r.center.x - len, r.center.y), Offset(r.center.x + len, r.center.y), stroke * 1.5f)
            drawLine(c, Offset(r.center.x, r.center.y - len), Offset(r.center.x, r.center.y + len), stroke * 1.5f)
        }
        LiveGrid.Square -> frame(1f)
        LiveGrid.Portrait45 -> frame(4f / 5f)
        LiveGrid.Story -> frame(9f / 16f)
    }
}

fun peakingColor(id: String): Int = when (id) {
    "red" -> 0xFFFF3B30.toInt()
    "white" -> 0xFFFFFFFF.toInt()
    "cyan" -> 0xFF1CDAFF.toInt()
    else -> 0xFF00FCA8.toInt()
}
