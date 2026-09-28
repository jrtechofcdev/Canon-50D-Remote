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
import com.example.ui.components.ValuePickerSheet
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
 * Tap to focus, camera zoom 1/5/10×, focus nudges, depth-of-field preview, exposure and shutter.
 */
@Composable
fun LiveViewScreen(
    app: TetherApp,
    onBack: () -> Unit,
    /** Where frames come from; the camera normally, a fixed picture in screenshot tests. */
    frames: kotlinx.coroutines.flow.Flow<ByteArray> = app.camera.liveFrames,
) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val connection by app.camera.state.collectAsState()
    val live by app.camera.live.collectAsState()
    val status by app.camera.status.collectAsState()
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    var frame by remember { mutableStateOf<LiveFrame?>(null) }
    var controls by remember { mutableStateOf(true) }
    var landscape by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var focusMark by remember { mutableStateOf<Offset?>(null) }
    val processor = remember { LiveProcessor(app.engine) }

    // The look on the picture: the session's, a chosen preset, or none.
    val lookParams: RenderParams? = remember(settings.livePresetId, settings.sessionPresetId, presets) {
        val id = if (settings.livePresetId == 0L) settings.sessionPresetId else settings.livePresetId
        presets.firstOrNull { it.id == id }?.let { RenderParams.from(it.settings) }
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

    // Start when the screen opens (and after a reconnect); stop when it closes.
    val connected = connection is ConnectionState.Connected
    LaunchedEffect(connected, settings.liveOnCamera) {
        if (connected) {
            app.camera.stopLiveView()
            app.camera.startLiveView(alsoOnCamera = settings.liveOnCamera)
        }
    }
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            app.camera.stopLiveView()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(landscape) {
        activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    // Frames: newest only; decoding and the look run off the main thread.
    LaunchedEffect(Unit) {
        frames.conflate().collect { jpeg ->
            val next = withContext(Dispatchers.Default) { runCatching { processor.process(jpeg, currentOptions) }.getOrNull() }
            if (next != null) frame = next
        }
    }
    LaunchedEffect(focusMark) {
        if (focusMark != null) { kotlinx.coroutines.delay(1500); focusMark = null }
    }
    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val f = frame
            if (f != null) {
                val pic = Size(f.picture.width.toFloat(), f.picture.height.toFloat())
                val fit = fitRect(box, pic)
                Zoomable(
                    key = "live",
                    onTap = { x, y ->
                        // Tap position → point on the picture (the picture is letterboxed in the box).
                        val px = (x * box.width - fit.left) / fit.width
                        val py = (y * box.height - fit.top) / fit.height
                        if (px in 0f..1f && py in 0f..1f) {
                            val cx = if (settings.liveMirror) 1f - px else px
                            if (settings.liveTapToFocus) {
                                focusMark = Offset(x * box.width, y * box.height)
                                app.camera.setLiveFocusPoint(cx, py)
                                app.camera.liveAutofocus()
                            }
                        } else controls = !controls
                    },
                ) {
                    val flip = Modifier.graphicsLayer { scaleX = if (settings.liveMirror) -1f else 1f }
                    Image(f.picture.asImageBitmap(), contentDescription = "Imagem ao vivo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().then(flip))
                    f.peaking?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().then(flip)) }
                    f.zebra?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().then(flip)) }
                    Canvas(Modifier.fillMaxSize()) { drawGuides(fitRect(size, pic), LiveGrid.of(settings.liveGrid)) }
                }
            } else {
                LiveWaiting(live, connected, Modifier.align(Alignment.Center))
            }
            val accent = Tone.Accent
            focusMark?.let { p ->
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(accent, Offset(p.x - 36.dp.toPx(), p.y - 36.dp.toPx()), Size(72.dp.toPx(), 72.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
        }

        // Clean monitor: one small button brings the controls back.
        if (!controls) {
            IconButton(
                onClick = { controls = true },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) { Icon(Icons.Outlined.Visibility, contentDescription = "Mostrar controles", tint = Color.White) }
            return@Box
        }

        // Top: back, status, look, options.
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.5f)).statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Live view", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    statusLine(live, lookParams != null, presets.firstOrNull { it.id == (if (settings.livePresetId == 0L) settings.sessionPresetId else settings.livePresetId) }?.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            IconButton(onClick = { landscape = !landscape }) { Icon(Icons.Outlined.ScreenRotation, contentDescription = "Girar a tela", tint = Color.White) }
            IconButton(onClick = { showOptions = true }) { Icon(Icons.Outlined.Layers, contentDescription = "Sobreposições e preset", tint = Color.White) }
            IconButton(onClick = { controls = false }) { Icon(Icons.Outlined.VisibilityOff, contentDescription = "Esconder controles", tint = Color.White) }
        }

        frame?.histogram?.let { h ->
            Histogram(h, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 12.dp).size(width = 120.dp, height = 56.dp))
        }

        // Bottom: exposure, focus and zoom, shutter.
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.55f)).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExposureStrip(status) { title, prop -> picker = title to prop }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val zoom = (live as? LiveState.Running)?.zoom ?: 1
                    listOf(1, 5, 10).forEach { z -> Pill("${z}×", zoom == z) { app.camera.setLiveZoom(z) } }
                    Pill("AF", false) { app.camera.liveAutofocus() }
                    Pill("◀◀", false) { app.camera.driveLens(-3) }
                    Pill("◀", false) { app.camera.driveLens(-1) }
                    Pill("▶", false) { app.camera.driveLens(1) }
                    Pill("▶▶", false) { app.camera.driveLens(3) }
                    HoldPill("DOF", onPress = { app.camera.depthOfFieldPreview(true) }, onRelease = { app.camera.depthOfFieldPreview(false) })
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).border(2.dp, Tone.Accent, CircleShape).padding(6.dp).clip(CircleShape)
                        .background(BrandGradient).clickable(enabled = connected) { app.camera.capture() },
                )
            }
            Text(
                "Toque para focar · pinça: lupa · ◀ ▶ foco fino (lente em AF)",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
            )
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

@Composable
private fun ExposureStrip(status: com.example.core.camera.CameraStatus, onPick: (String, Int) -> Unit) {
    fun text(prop: Int) = status.value(prop)?.let { EosValues.label(prop, it) } ?: "--"
    val mode = status.value(EosProp.SHOOTING_MODE)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Value("Tv", text(EosProp.SHUTTER_SPEED), mode in setOf(1, 3, 4)) { onPick("Velocidade", EosProp.SHUTTER_SPEED) }
        Value("Av", text(EosProp.APERTURE), mode in setOf(2, 3, 4)) { onPick("Abertura", EosProp.APERTURE) }
        Value("ISO", text(EosProp.ISO), true) { onPick("ISO", EosProp.ISO) }
        Value("±EV", text(EosProp.EXPOSURE_COMPENSATION), mode != 3) { onPick("Compensação", EosProp.EXPOSURE_COMPENSATION) }
        Value("WB", text(EosProp.WHITE_BALANCE), true) { onPick("Balanço de branco", EosProp.WHITE_BALANCE) }
    }
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
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Tone.OnAccent else Color.White,
        modifier = Modifier.clip(CircleShape).background(if (selected) Tone.Accent else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Active only while held, like the camera's depth-of-field preview button. */
@Composable
private fun HoldPill(label: String, onPress: () -> Unit, onRelease: () -> Unit) {
    var held by remember { mutableStateOf(false) }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (held) Tone.OnAccent else Color.White,
        modifier = Modifier.clip(CircleShape).background(if (held) Tone.Accent else Color.White.copy(alpha = 0.14f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    onPress()
                    tryAwaitRelease()
                    held = false
                    onRelease()
                })
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Histogram(bins: List<IntArray>, modifier: Modifier) {
    val colors = listOf(Color(0x66FFFFFF), Color(0xFFE5484D), Color(0xFF46A758), Color(0xFF3E63DD))
    Canvas(modifier.clip(MaterialTheme.shapes.extraSmall).background(Color.Black.copy(alpha = 0.5f))) {
        val max = bins.drop(1).maxOf { it.max() }.coerceAtLeast(1)
        val bw = size.width / 64
        bins.forEachIndexed { c, b ->
            if (c == 0) return@forEachIndexed
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
