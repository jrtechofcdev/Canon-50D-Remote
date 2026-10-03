package com.example.ui.crop

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Grid3x3
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.analysis.CullMath
import com.example.core.data.Photo
import com.example.core.render.CropGeometry
import com.example.core.render.CropGroup
import com.example.core.render.CropRatio
import com.example.core.render.CropSpec
import com.example.core.render.HorizonDetector
import com.example.core.render.Looks
import com.example.ui.photos.SheetChip
import com.example.ui.preset.AdjustSlider
import com.example.ui.preset.SliderSpec
import com.example.ui.preset.lookSettings
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll

private enum class CropTab(val label: String) { Ratio("Proporção"), Straighten("Endireitar"), Turn("Girar") }
private enum class Grid(val label: String) { Thirds("Terços"), Golden("Áurea"), Fine("Grade fina"), None("Sem grade") }
private enum class Handle { TL, TR, BL, BR, L, T, R, B, Move }

/**
 * Lightroom-style crop for one photo: frame with handles (free or locked to a preset such as
 * Instagram 4:5 or Stories 9:16), drag inside to frame, straighten (slider, Auto, or draw the horizon),
 * quarter turns and flips. The original file never changes; the crop goes with the photo's edit.
 */
@Composable
fun CropScreen(app: TetherApp, photoId: Long, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Photo?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var spec by remember { mutableStateOf(CropSpec()) }
    var ratio by remember { mutableStateOf(CropRatio.FREE) }
    var landscape by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(Grid.Thirds) }
    var drawHorizon by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(photoId) {
        val p = app.database.photoDao().get(photoId) ?: return@LaunchedEffect
        photo = p
        val presets = app.presets.all()
        val look = lookSettings(p, presets)
        val uri = p.displayUri ?: return@LaunchedEffect
        // The crop is shown over the look, so the framing is judged on the final colours.
        source = (look?.let { app.looks.render(uri, it, Looks.EDITOR_EDGE) } ?: app.looks.base(uri, Looks.EDITOR_EDGE))
        CropSpec.decode(p.crop)?.let { saved ->
            spec = saved
            ratio = CropRatio.of(saved.ratio)
            landscape = saved.width * (source?.width ?: 1) >= saved.height * (source?.height ?: 1)
        }
    }

    // The picture after quarter turns and flips; the straighten angle is drawn on top of it.
    val turned = remember(source, spec.quarterTurns, spec.flipH, spec.flipV) {
        source?.let { src ->
            if (spec.quarterTurns % 4 == 0 && !spec.flipH && !spec.flipV) src
            else Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply {
                postRotate(90f * spec.quarterTurns)
                postScale(if (spec.flipH) -1f else 1f, if (spec.flipV) -1f else 1f)
            }, true)
        }
    }
    val image: ImageBitmap? = remember(turned) { turned?.asImageBitmap() }
    val w = (turned?.width ?: 1).toFloat()
    val h = (turned?.height ?: 1).toFloat()
    val aspect = CropGeometry.aspect(ratio, w, h, landscape)

    fun applyRatio(r: CropRatio, land: Boolean) {
        ratio = r
        landscape = land
        val a = CropGeometry.aspect(r, w, h, land)
        val cx = (spec.left + spec.right) / 2
        val cy = (spec.top + spec.bottom) / 2
        spec = CropGeometry.largest(spec, w, h, a, cx, cy).copy(ratio = r.id)
    }

    fun setAngle(deg: Float) {
        spec = CropGeometry.fitAfterRotation(spec.copy(angle = deg.coerceIn(-45f, 45f)), w, h)
    }

    fun turn(by: Int) {
        val next = spec.copy(quarterTurns = ((spec.quarterTurns + by) % 4 + 4) % 4, angle = 0f)
        val (tw, th) = CropGeometry.turnedSize(source?.width ?: 1, source?.height ?: 1, next.quarterTurns)
        // After a quarter turn the old rectangle no longer means anything: start again from the widest frame.
        spec = CropGeometry.largest(next, tw.toFloat(), th.toFloat(), CropGeometry.aspect(ratio, tw.toFloat(), th.toFloat(), landscape))
    }

    fun save() {
        val p = photo ?: return
        saving = true
        scope.launch {
            // The network's pixel size, turned the same way as the frame.
            val target = ratio.target(portrait = spec.height * h > spec.width * w)
            app.looks.setCrop(p, spec.copy(ratio = ratio.id, targetW = target.first, targetH = target.second))
            onDone()
        }
    }

    BackHandler { onDone() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(Tone.Background).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Cancelar") }
            Column(Modifier.weight(1f)) {
                Text("Recortar", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        ratio.label,
                        ratio.detail.takeIf { ratio.targetW > 0 }?.substringBefore(" ("),
                        "%+.1f°".format(Locale("pt", "BR"), spec.angle).takeIf { abs(spec.angle) >= 0.05f },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { grid = Grid.entries[(grid.ordinal + 1) % Grid.entries.size] }) {
                Icon(Icons.Outlined.Grid3x3, contentDescription = "Grade: ${grid.label}", tint = if (grid == Grid.None) Tone.TextMuted else Tone.Text)
            }
            IconButton(onClick = {
                ratio = CropRatio.FREE
                spec = CropSpec()
            }) { Icon(Icons.Outlined.RestartAlt, contentDescription = "Redefinir") }
            IconButton(onClick = { save() }, enabled = image != null && !saving) {
                Icon(Icons.Filled.Check, contentDescription = "Aplicar recorte", tint = Tone.Accent)
            }
        }

        // Standing: the photo on top, the tools below. Lying down: the photo on the left and the tools in
        // a column on the right, so the picture keeps the screen's height.
        val lying = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val canvasArea: @Composable (Modifier) -> Unit = { mod ->
            Box(mod, contentAlignment = Alignment.Center) {
                if (image == null) {
                    CircularProgressIndicator(color = Tone.Info)
                } else {
                    CropCanvas(
                        image = image,
                        spec = spec,
                        aspect = aspect,
                        grid = grid,
                        drawHorizon = drawHorizon,
                        onChange = { spec = it },
                        onHorizon = { lineDeg ->
                            drawHorizon = false
                            // Level the traced line: turn the picture by the opposite of its tilt.
                            setAngle(spec.angle - lineDeg)
                            message = "Horizonte nivelado (${"%+.1f°".format(Locale("pt", "BR"), spec.angle)})"
                        },
                    )
                }
                message?.let {
                    Text(
                        it,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    LaunchedEffect(it) { kotlinx.coroutines.delay(2500); message = null }
                }
            }
        }
        val toolsArea: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.background(Tone.Background).navigationBarsPadding()) {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Tone.Background,
                    contentColor = Tone.Text,
                    indicator = { positions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = Tone.Accent) },
                ) {
                    CropTab.entries.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i; drawHorizon = false }, text = { Text(t.label) }, unselectedContentColor = Tone.TextMuted)
                    }
                }
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    when (CropTab.entries[tab]) {
                        CropTab.Ratio -> RatioPanel(ratio, landscape, onPick = { applyRatio(it, if (it == ratio) landscape else it.defaultLandscape(w, h)) }, onSwap = { applyRatio(ratio, !landscape) })
                        CropTab.Straighten -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdjustSlider(SliderSpec("angle", "Ângulo", -45f, 45f, decimals = 1), spec.angle, { setAngle(it) }, {})
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val src = turned ?: return@OutlinedButton
                                    scope.launch {
                                        val found = withContext(Dispatchers.Default) { autoHorizon(src) }
                                        if (found == null) message = "Não achei linhas retas nesta foto"
                                        else { setAngle(found); message = "Auto: ${"%+.1f°".format(Locale("pt", "BR"), found)}" }
                                    }
                                }) { Text("Auto") }
                                OutlinedButton(onClick = { drawHorizon = !drawHorizon }) {
                                    Text(if (drawHorizon) "Trace na foto…" else "Traçar horizonte", color = if (drawHorizon) Tone.Accent else Tone.Text)
                                }
                                OutlinedButton(onClick = { setAngle(0f) }) { Text("Zerar") }
                            }
                        }
                        CropTab.Turn -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TurnButton(Icons.AutoMirrored.Outlined.RotateLeft, "Girar à esquerda") { turn(-1) }
                            TurnButton(Icons.AutoMirrored.Outlined.RotateRight, "Girar à direita") { turn(1) }
                            TurnButton(Icons.Outlined.Flip, "Espelhar") { spec = spec.copy(flipH = !spec.flipH, left = 1f - spec.right, right = 1f - spec.left, angle = -spec.angle) }
                            TurnButton(Icons.Outlined.ScreenRotationAlt, "Virar de cabeça") { spec = spec.copy(flipV = !spec.flipV, top = 1f - spec.bottom, bottom = 1f - spec.top, angle = -spec.angle) }
                        }
                    }
                }
            }
        }
        if (lying) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                canvasArea(Modifier.weight(1f).fillMaxHeight())
                toolsArea(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            canvasArea(Modifier.weight(1f).fillMaxWidth())
            toolsArea(Modifier.fillMaxWidth())
        }
    }
}

private fun autoHorizon(src: Bitmap): Float? {
    val small = Bitmap.createScaledBitmap(src, 480, (480f * src.height / src.width).toInt().coerceAtLeast(1), true)
    val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
    val found = HorizonDetector.detect(CullMath.luma(px), small.width, small.height)
    if (small !== src) small.recycle()
    return found
}

@Composable
private fun TurnButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = Tone.Text)
        Text("  $label", color = Tone.Text)
    }
}

@Composable
private fun RatioPanel(ratio: CropRatio, landscape: Boolean, onPick: (CropRatio) -> Unit, onSwap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CropGroup.entries.forEach { group ->
            Text(group.label, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CropRatio.entries.filter { it.group == group }.forEach { r -> SheetChip(r.label, r == ratio) { onPick(r) } }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ratio.detail, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, modifier = Modifier.weight(1f), maxLines = 2)
            if (ratio.locked && !(ratio.w == ratio.h && ratio.w > 0)) {
                OutlinedButton(onClick = onSwap) { Text(if (landscape) "Paisagem" else "Retrato") }
            }
        }
    }
}

/**
 * The picture (straightened) with the crop frame on top. Corner and edge handles resize, dragging inside
 * moves the frame over the picture; locked ratios keep their shape. The frame never leaves the picture.
 */
@Composable
private fun CropCanvas(
    image: ImageBitmap,
    spec: CropSpec,
    aspect: Float?,
    grid: Grid,
    drawHorizon: Boolean,
    onChange: (CropSpec) -> Unit,
    onHorizon: (Float) -> Unit,
) {
    val w = image.width.toFloat()
    val h = image.height.toFloat()
    var active by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val current by androidx.compose.runtime.rememberUpdatedState(spec)
    val accent = Tone.Accent

    // Where the (unrotated) picture sits on screen: fitted with room for the handles.
    fun frame(size: Size): Rect {
        val pad = 28f
        val s = min((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val dw = w * s
        val dh = h * s
        return Rect(Offset((size.width - dw) / 2, (size.height - dh) / 2), Size(dw, dh))
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(drawHorizon, aspect, w, h) {
                var handle = Handle.Move
                var start = current
                var acc = Offset.Zero
                detectDragGestures(
                    onDragStart = { at ->
                        active = true
                        if (drawHorizon) { line = at to at; return@detectDragGestures }
                        val f = frame(canvasSize)
                        val r = Rect(f.left + current.left * f.width, f.top + current.top * f.height, f.left + current.right * f.width, f.top + current.bottom * f.height)
                        handle = hit(r, at, 36.dp.toPx())
                        start = current
                        acc = Offset.Zero
                    },
                    onDragEnd = {
                        active = false
                        line?.let { (a, b) ->
                            line = null
                            if (hypot(b.x - a.x, b.y - a.y) > 40f) {
                                var deg = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
                                // A line drawn right-to-left, or a vertical one (a wall), both count.
                                if (deg > 90f) deg -= 180f
                                if (deg < -90f) deg += 180f
                                if (abs(deg) > 45f) deg -= if (deg > 0) 90f else -90f
                                onHorizon(deg)
                            }
                        }
                    },
                    onDragCancel = { active = false; line = null },
                    onDrag = { change, delta ->
                        change.consume()
                        if (drawHorizon) { line = line?.let { it.first to change.position }; return@detectDragGestures }
                        val f = frame(canvasSize)
                        acc += delta
                        val dx = acc.x / f.width
                        val dy = acc.y / f.height
                        onChange(dragged(start, handle, dx, dy, aspect, w, h))
                    },
                )
            },
    ) {
        canvasSize = size
        val f = frame(size)
        // Straightened picture.
        withTransform({
            rotate(spec.angle, pivot = f.center)
        }) {
            drawImage(image, dstOffset = IntOffset(f.left.toInt(), f.top.toInt()), dstSize = IntSize(f.width.toInt(), f.height.toInt()))
        }
        val r = Rect(f.left + spec.left * f.width, f.top + spec.top * f.height, f.left + spec.right * f.width, f.top + spec.bottom * f.height)
        // Dim outside the frame.
        val dim = Color.Black.copy(alpha = if (active) 0.45f else 0.7f)
        drawRect(dim, Offset.Zero, Size(size.width, r.top))
        drawRect(dim, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
        drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(dim, Offset(r.right, r.top), Size(size.width - r.right, r.height))
        // Frame, grid and handles.
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.5.dp.toPx()))
        drawGrid(r, if (active && grid == Grid.None) Grid.Thirds else grid, active)
        drawHandles(r, 3.dp.toPx(), 20.dp.toPx())
        line?.let { (a, b) -> drawLine(accent, a, b, strokeWidth = 2.dp.toPx()) }
    }
}

private fun hit(r: Rect, p: Offset, slop: Float): Handle {
    fun near(a: Float, b: Float) = abs(a - b) <= slop
    val l = near(p.x, r.left); val rt = near(p.x, r.right); val t = near(p.y, r.top); val b = near(p.y, r.bottom)
    val inX = p.x in (r.left - slop)..(r.right + slop)
    val inY = p.y in (r.top - slop)..(r.bottom + slop)
    return when {
        l && t -> Handle.TL
        rt && t -> Handle.TR
        l && b -> Handle.BL
        rt && b -> Handle.BR
        l && inY -> Handle.L
        rt && inY -> Handle.R
        t && inX -> Handle.T
        b && inX -> Handle.B
        else -> Handle.Move
    }
}

/** The crop after dragging [handle] by (dx, dy) fractions from [start], clamped to the picture. */
private fun dragged(start: CropSpec, handle: Handle, dx: Float, dy: Float, aspect: Float?, w: Float, h: Float): CropSpec {
    if (handle == Handle.Move) {
        // Each axis on its own, so sliding along an edge still works.
        val sx = CropGeometry.towards(start, start.copy(left = start.left + dx, right = start.right + dx), w, h)
        return CropGeometry.towards(sx, sx.copy(top = sx.top + dy, bottom = sx.bottom + dy), w, h)
    }
    var l = start.left; var t = start.top; var r = start.right; var b = start.bottom
    when (handle) {
        Handle.TL -> { l += dx; t += dy }
        Handle.TR -> { r += dx; t += dy }
        Handle.BL -> { l += dx; b += dy }
        Handle.BR -> { r += dx; b += dy }
        Handle.L -> l += dx
        Handle.R -> r += dx
        Handle.T -> t += dy
        Handle.B -> b += dy
        Handle.Move -> Unit
    }
    if (aspect != null) {
        // Keep the shape: width drives height (corners, side edges), height drives width (top/bottom edges).
        val pw = (r - l) * w
        val ph = (b - t) * h
        when (handle) {
            Handle.T, Handle.B -> {
                val nw = ph * aspect / w
                val cx = (start.left + start.right) / 2
                l = cx - nw / 2; r = cx + nw / 2
            }
            Handle.L, Handle.R -> {
                val nh = pw / aspect / h
                val cy = (start.top + start.bottom) / 2
                t = cy - nh / 2; b = cy + nh / 2
            }
            Handle.TL, Handle.TR -> t = b - pw / aspect / h
            Handle.BL, Handle.BR -> b = t + pw / aspect / h
            Handle.Move -> Unit
        }
    }
    if (r - l < 0 || b - t < 0) return start
    return CropGeometry.towards(start, start.copy(left = l, top = t, right = r, bottom = b), w, h)
}

private fun DrawScope.drawGrid(r: Rect, grid: Grid, active: Boolean) {
    val c = Color.White.copy(alpha = if (active) 0.7f else 0.4f)
    val stroke = 1.dp.toPx()
    fun v(f: Float) = drawLine(c, Offset(r.left + r.width * f, r.top), Offset(r.left + r.width * f, r.bottom), stroke)
    fun hz(f: Float) = drawLine(c, Offset(r.left, r.top + r.height * f), Offset(r.right, r.top + r.height * f), stroke)
    when (grid) {
        Grid.Thirds -> listOf(1f / 3, 2f / 3).forEach { v(it); hz(it) }
        Grid.Golden -> listOf(0.382f, 0.618f).forEach { v(it); hz(it) }
        Grid.Fine -> (1 until 8).forEach { v(it / 8f); hz(it / 8f) }
        Grid.None -> Unit
    }
}

private fun DrawScope.drawHandles(r: Rect, thick: Float, len: Float) {
    val c = Color.White
    // L-shaped corners and short bars at the middle of each side, like Lightroom.
    for ((x, y, sx, sy) in listOf(
        Quad(r.left, r.top, 1f, 1f), Quad(r.right, r.top, -1f, 1f), Quad(r.left, r.bottom, 1f, -1f), Quad(r.right, r.bottom, -1f, -1f),
    )) {
        drawLine(c, Offset(x, y), Offset(x + sx * len, y), thick)
        drawLine(c, Offset(x, y), Offset(x, y + sy * len), thick)
    }
    drawLine(c, Offset(r.center.x - len / 2, r.top), Offset(r.center.x + len / 2, r.top), thick)
    drawLine(c, Offset(r.center.x - len / 2, r.bottom), Offset(r.center.x + len / 2, r.bottom), thick)
    drawLine(c, Offset(r.left, r.center.y - len / 2), Offset(r.left, r.center.y + len / 2), thick)
    drawLine(c, Offset(r.right, r.center.y - len / 2), Offset(r.right, r.center.y + len / 2), thick)
}

private data class Quad(val x: Float, val y: Float, val sx: Float, val sy: Float)
