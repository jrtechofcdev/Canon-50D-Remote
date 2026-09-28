package com.example.ui.preset

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.core.preset.CurvePoint
import com.example.core.render.CurveMath
import com.example.ui.theme.ReadoutStyle
import com.example.ui.theme.Tone
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One adjustment. Dragging updates live ([onChange]); lifting the finger commits ([onCommit], which the
 * editor uses for undo). Double tap on the label resets to the default, like Lightroom.
 */
@Composable
fun AdjustSlider(spec: SliderSpec, value: Float, onChange: (Float) -> Unit, onCommit: () -> Unit) {
    val changed = kotlin.math.abs(value - spec.default) > 1e-4f
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(spec.key) { detectTapGestures(onDoubleTap = { onChange(spec.default); onCommit() }) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium, color = if (changed) Tone.Text else Tone.TextMuted, modifier = Modifier.weight(1f))
            Text(
                format(value, spec),
                style = ReadoutStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                color = if (changed) Tone.Accent else Tone.TextMuted,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            if (spec.track != Track.Plain) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(trackBrush(spec.track)),
                )
            }
            Slider(
                value = value.coerceIn(spec.min, spec.max),
                onValueChange = { v -> onChange(if (spec.decimals == 0) v.roundToInt().toFloat() else v) },
                onValueChangeFinished = onCommit,
                valueRange = spec.min..spec.max,
                colors = SliderDefaults.colors(
                    thumbColor = Tone.Text,
                    activeTrackColor = if (spec.track == Track.Plain) Tone.TextMuted else Color.Transparent,
                    inactiveTrackColor = if (spec.track == Track.Plain) Tone.SurfaceHigh else Color.Transparent,
                ),
                modifier = Modifier.height(32.dp).semantics { contentDescription = spec.label },
            )
        }
    }
}

private fun format(v: Float, spec: SliderSpec): String = when {
    spec.decimals > 0 -> String.format(Locale.US, if (v > 0 && spec.min < 0) "+%.${spec.decimals}f" else "%.${spec.decimals}f", v)
    spec.min < 0 && v > 0 -> "+${v.roundToInt()}"
    else -> "${v.roundToInt()}"
}

private fun trackBrush(track: Track): Brush = when (track) {
    Track.Temperature -> Brush.horizontalGradient(listOf(Color(0xFF3E7BD6), Color(0xFFE8E8E8), Color(0xFFE8B23A)))
    Track.Tint -> Brush.horizontalGradient(listOf(Color(0xFF46A758), Color(0xFFE8E8E8), Color(0xFFD6409F)))
    Track.Hue -> Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.8f, 0.9f) })
    Track.Plain -> Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
}

/**
 * Point curve like Lightroom's: drag a point to move it, touch an empty spot to add one,
 * double tap a point to remove it. The two end points only move vertically.
 */
@Composable
fun CurveEditor(points: List<CurvePoint>, color: Color, onChange: (List<CurvePoint>) -> Unit, onCommit: () -> Unit) {
    val current by rememberUpdatedState(points)
    var dragging by remember { mutableIntStateOf(-1) }
    val curve = CurveMath.spline(points)

    BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(1f)) {
        val gridColor = Tone.Line
        val knobFill = Tone.Background
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.small)
                .background(Tone.Surface)
                .border(1.dp, Tone.Line, MaterialTheme.shapes.small)
                .pointerInput(Unit) {
                    fun toPx(p: CurvePoint) = Offset(p.x / 255f * size.width, (1f - p.y / 255f) * size.height)
                    fun toPoint(o: Offset) = CurvePoint(
                        (o.x / size.width * 255f).roundToInt().coerceIn(0, 255),
                        ((1f - o.y / size.height) * 255f).roundToInt().coerceIn(0, 255),
                    )
                    fun nearest(o: Offset): Int {
                        val i = current.indices.minByOrNull { (toPx(current[it]) - o).getDistance() } ?: -1
                        return if (i >= 0 && (toPx(current[i]) - o).getDistance() < 28.dp.toPx()) i else -1
                    }
                    detectTapGestures(
                        onDoubleTap = { o ->
                            val i = nearest(o)
                            if (i > 0 && i < current.lastIndex) { onChange(current.filterIndexed { k, _ -> k != i }); onCommit() }
                        },
                        onTap = { o ->
                            if (nearest(o) < 0) {
                                val x = toPoint(o).x
                                val y = (curve(x / 255f) * 255f).roundToInt()
                                if (current.none { it.x == x }) { onChange((current + CurvePoint(x, y)).sortedBy { it.x }); onCommit() }
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    fun toPx(p: CurvePoint) = Offset(p.x / 255f * size.width, (1f - p.y / 255f) * size.height)
                    detectDragGestures(
                        onDragStart = { o ->
                            dragging = current.indices.minByOrNull { (toPx(current[it]) - o).getDistance() }
                                ?.takeIf { (toPx(current[it]) - o).getDistance() < 36.dp.toPx() } ?: -1
                        },
                        onDragEnd = { if (dragging >= 0) onCommit(); dragging = -1 },
                        onDragCancel = { dragging = -1 },
                        onDrag = { change, _ ->
                            val i = dragging
                            if (i < 0) return@detectDragGestures
                            val pts = current.toMutableList()
                            val y = ((1f - change.position.y / size.height) * 255f).roundToInt().coerceIn(0, 255)
                            val x = if (i == 0 || i == pts.lastIndex) pts[i].x else {
                                val lo = pts[i - 1].x + 1
                                val hi = pts[i + 1].x - 1
                                (change.position.x / size.width * 255f).roundToInt().coerceIn(lo, hi)
                            }
                            pts[i] = CurvePoint(x, y)
                            onChange(pts)
                        },
                    )
                },
        ) {
            val grid = gridColor
            for (k in 1..3) {
                val f = k / 4f
                drawLine(grid, Offset(size.width * f, 0f), Offset(size.width * f, size.height))
                drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f))
            }
            drawLine(grid.copy(alpha = 0.6f), Offset(0f, size.height), Offset(size.width, 0f))
            val path = Path()
            for (i in 0..128) {
                val x = i / 128f
                val px = x * size.width
                val py = (1f - curve(x)) * size.height
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            points.forEachIndexed { i, p ->
                val c = Offset(p.x / 255f * size.width, (1f - p.y / 255f) * size.height)
                drawCircle(if (i == dragging) color else knobFill, radius = 7.dp.toPx(), center = c)
                drawCircle(color, radius = 7.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

/** Colour-grading wheel: angle = hue, distance from the centre = saturation. */
@Composable
fun ColorWheel(hue: Float, sat: Float, onChange: (hue: Float, sat: Float) -> Unit, onCommit: () -> Unit) {
    fun update(o: Offset, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f
        val dx = o.x - cx; val dy = o.y - cy
        val angle = ((Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
        val r = (hypot(dx, dy) / (w / 2f)).coerceIn(0f, 1f)
        onChange(angle.roundToInt().toFloat(), (r * 100f).roundToInt().toFloat())
    }
    Canvas(
        Modifier
            .size(180.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> update(o, size.width.toFloat(), size.height.toFloat()); onCommit() }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onCommit,
                    onDrag = { change, _ -> update(change.position, size.width.toFloat(), size.height.toFloat()) },
                )
            },
    ) {
        val radius = size.minDimension / 2f
        val colors = (0..12).map { Color.hsv((360f - it * 30f) % 360f, 1f, 1f) }
        drawCircle(Brush.sweepGradient(colors), radius)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF8A8A8A), Color.Transparent), radius = radius), radius)
        val a = Math.toRadians(hue.toDouble())
        val d = sat / 100f * radius
        val dot = Offset(center.x + (cos(a) * d).toFloat(), center.y - (sin(a) * d).toFloat())
        drawCircle(Color.White, 9.dp.toPx(), dot)
        drawCircle(Color.Black, 9.dp.toPx(), dot, style = Stroke(2.dp.toPx()))
    }
}

/** Round colour swatch used to pick a Colour Mixer band or a grading zone. */
@Composable
fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 0.dp, if (selected) Tone.Text else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    )
}

@Composable
fun PanelTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
        content()
    }
}
