package com.example.ui.intro

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Brand
import com.example.ui.theme.ReadoutStyle
import com.example.ui.theme.Tone
import com.example.ui.theme.WordmarkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** "Ver agora" in Ajustes asks for the opening again through here. */
object IntroRequests {
    val replay = MutableStateFlow(0)
}

/**
 * The opening, a photograph being taken with the brand as the subject, in about three seconds:
 *
 * 1. The four blocks of the Jr_Tech_OFC symbol fly in along their slant and lock together.
 * 2. The AF frame hunts around the symbol and locks in mint, like the 50D confirming focus.
 * 3. The aperture's six blades close and open (the shot), with a soft flash.
 * 4. "JR C50D Sync" settles letter by letter, over the exposure the 50mm f/1.8 would use.
 *
 * A tap anywhere skips it. [frozenAt] draws one moment still (screenshots and tests).
 */
@Composable
fun BrandIntro(onDone: () -> Unit, frozenAt: Int? = null) {
    val clock = remember { Animatable((frozenAt ?: 0).toFloat()) }
    val leave = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var finished by remember { mutableStateOf(false) }

    fun finish(fast: Boolean) {
        if (finished) return
        finished = true
        scope.launch {
            leave.animateTo(1f, tween(if (fast) 220 else EXIT, easing = FastOutSlowInEasing))
            onDone()
        }
    }

    if (frozenAt == null) {
        LaunchedEffect(Unit) {
            launch {
                // A tick when the focus locks and another when the shutter fires, felt more than heard.
                kotlinx.coroutines.delay(AF_LOCK.toLong())
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                kotlinx.coroutines.delay((IRIS_SHUT - AF_LOCK).toLong())
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            clock.animateTo(LEAVE.toFloat(), tween(LEAVE, easing = LinearEasing))
            finish(fast = false)
        }
    }

    val t = frozenAt?.toFloat() ?: clock.value
    val lying = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = 1f - leave.value
                val k = 1f + 0.06f * leave.value
                scaleX = k; scaleY = k
            }
            .background(Brush.radialGradient(listOf(Color(0xFF1F1F1F), Color(0xFF060606)), radius = 1400f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish(fast = true) }
            .semantics { contentDescription = "Abertura do JR C50D Sync. Toque para pular." },
    ) {
        val symbolSize = if (lying) 104.dp else 140.dp
        // After the shot the symbol rises to make room for the name.
        val rise = seg(t, 1450, 1950, Emphasized)
        val lift = (if (lying) 58.dp else 74.dp) * rise
        val shrink = 1f - 0.18f * rise

        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f - lift.toPx())
            val unit = symbolSize.toPx() / 500f * shrink
            drawTiles(t, c, unit)
            drawFocusFrame(t, c, unit)
            drawIris(t, c, hypot(size.width, size.height) / 2f + 40f)
            val flash = seg(t, IRIS_OPEN_START, IRIS_OPEN_START + 380, FastOutSlowInEasing)
            if (t >= IRIS_OPEN_START && flash < 1f) drawRect(Color.White.copy(alpha = 0.55f * (1f - flash)))
        }

        Column(
            Modifier.align(Alignment.Center).offset(y = (if (lying) 64.dp else 86.dp) - lift * 0.35f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(t)
            Spacer(Modifier.height(10.dp))
            Readout(t)
            Spacer(Modifier.height(8.dp))
            val tag = seg(t, 2050, 2450, Emphasized)
            Text(
                "Captura vinculada · Canon 50D",
                style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted,
                modifier = Modifier.graphicsLayer { alpha = tag; translationY = (1f - tag) * 12f },
            )
        }
        val sign = seg(t, 2150, 2550, Emphasized)
        Text(
            "Jr_Tech_OFC · Nossa Arte, Seu Sucesso",
            style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp).graphicsLayer { alpha = sign },
        )
    }
}

/** "JR C50D Sync": each letter rises into place a beat after the one before, the tracking closing in like the logo. */
@Composable
private fun Wordmark(t: Float) {
    val name = "JR C50D Sync"
    val track = 14f - 11f * seg(t, 1550, 2300, Emphasized)
    Row(horizontalArrangement = Arrangement.spacedBy(track.dp)) {
        name.forEachIndexed { i, ch ->
            val e = seg(t, 1550 + i * 45, 1550 + i * 45 + 420, Emphasized)
            Text(
                ch.toString(),
                style = WordmarkStyle.copy(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
                color = Tone.Text,
                modifier = Modifier.graphicsLayer { alpha = e; translationY = (1f - e) * 26f },
            )
        }
    }
}

/**
 * The top LCD of the 50D finding the exposure for the 50mm f/1.8: the values tick toward the settled
 * reading, with the green focus-confirm dot of the viewfinder.
 */
@Composable
private fun Readout(t: Float) {
    val e = seg(t, 1750, 2350, LinearEasing)
    val shutter = listOf("1/30", "1/60", "1/125")
    val aperture = listOf("F5.6", "F4.0", "F2.8", "F1.8")
    val iso = listOf("ISO 1600", "ISO 800", "ISO 400")
    fun <T> pick(list: List<T>) = list[(e * (list.size - 1) + 0.0001f).toInt().coerceIn(0, list.size - 1)]
    val shown = seg(t, 1700, 1950, Emphasized)
    val locked = e >= 1f
    Row(
        Modifier.graphicsLayer { alpha = shown },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (locked) Brand.Mint else Tone.Line))
        val style = ReadoutStyle.copy(fontSize = 15.sp, lineHeight = 18.sp)
        Text(pick(shutter), style = style, color = Tone.Text)
        Text(pick(aperture), style = style, color = if (locked) Brand.Mint else Tone.Text)
        Text(pick(iso), style = style, color = Tone.Text)
    }
}

/** The four blocks of the symbol, each arriving along its slant from its own side. */
private fun DrawScope.drawTiles(t: Float, c: Offset, unit: Float) {
    val brush = Brush.linearGradient(listOf(Brand.Mint, Brand.Cyan), start = Offset(150f, 40f), end = Offset(330f, 460f))
    // Where each block comes from, in symbol units: big top from upper right, small ones from the sides,
    // big bottom from lower left, all along the ~16° slant of the logo.
    val from = listOf(Offset(260f, -640f), Offset(720f, -110f), Offset(-720f, 110f), Offset(-260f, 640f))
    translate(c.x - 250f * unit, c.y - 250f * unit) {
        scale(unit, unit, pivot = Offset.Zero) {
            TILES.forEachIndexed { i, path ->
                val e = seg(t, 60 + i * 90, 60 + i * 90 + 640, Overshoot)
                if (e <= 0f) return@forEachIndexed
                val o = from[i] * (1f - e)
                translate(o.x, o.y) {
                    rotate((1f - e) * -14f, pivot = Offset(250f, 250f)) {
                        drawPath(path, brush, alpha = seg(t, 60 + i * 90, 60 + i * 90 + 260, LinearEasing))
                    }
                }
            }
        }
    }
}

/** The AF frame: four corners that close in on the symbol, hunt once, and lock in mint. */
private fun DrawScope.drawFocusFrame(t: Float, c: Offset, unit: Float) {
    if (t < 620 || t > 1700) return
    val e = seg(t, 620, AF_LOCK, Emphasized)
    val hunt = (sin(e * PI * 2.5) * 0.09 * (1f - e)).toFloat()
    val k = 1f + 0.75f * (1f - e) + hunt
    val half = 268f * unit * k
    val arm = 62f * unit
    val locked = t >= AF_LOCK
    val fade = 1f - seg(t, 1350, 1650, LinearEasing)
    val color = (if (locked) Brand.Mint else Color.White).copy(alpha = seg(t, 620, 760, LinearEasing) * fade)
    val w = 3.dp.toPx()
    for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
        val corner = Offset(c.x + sx * half, c.y + sy * half)
        drawLine(color, corner, corner + Offset(-sx * arm, 0f), w, StrokeCap.Round)
        drawLine(color, corner, corner + Offset(0f, -sy * arm), w, StrokeCap.Round)
    }
    // The focus-confirm blink in the middle, once.
    val blink = seg(t, AF_LOCK, AF_LOCK + 260, LinearEasing)
    if (locked && blink < 1f) drawCircle(Brand.Mint.copy(alpha = 1f - blink), 5.dp.toPx() * (1f + blink), c)
}

/** The aperture: a six-bladed hexagon closing over the frame and opening again, the blades turning as it moves. */
private fun DrawScope.drawIris(t: Float, c: Offset, open: Float) {
    if (t < IRIS_SHUT_START || t > IRIS_OPEN_END) return
    val closed = if (t < IRIS_SHUT) seg(t, IRIS_SHUT_START, IRIS_SHUT, FastOutSlowInEasing)
    else 1f - seg(t, IRIS_OPEN_START, IRIS_OPEN_END, Emphasized)
    if (closed <= 0f) return
    val r = open * (1f - closed)
    val turn = closed * 60f
    val hex = (0 until 6).map { i ->
        val a = Math.toRadians((i * 60f + turn).toDouble())
        Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
    }
    val shade = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        moveTo(hex[0].x, hex[0].y)
        hex.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(shade, Color(0xFF0B0B0B))
    // Blade edges: each side of the opening carries on outward, the way real blades overlap.
    val edge = Color(0xFF3A3A3A)
    for (i in 0 until 6) {
        val a = hex[i]
        val b = hex[(i + 1) % 6]
        val d = (b - a).let { it / maxOf(it.getDistance(), 0.001f) }
        drawLine(edge, a, a + d * open * 1.6f, 1.5.dp.toPx())
    }
}

/** Progress of a segment from [start] to [end] (ms), eased, clamped to 0..1. */
private fun seg(t: Float, start: Int, end: Int, easing: Easing): Float =
    easing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Overshoot = CubicBezierEasing(0.3f, 1.45f, 0.55f, 1f)

private const val AF_LOCK = 1050
private const val IRIS_SHUT_START = 1120
private const val IRIS_SHUT = 1240
private const val IRIS_OPEN_START = 1260
private const val IRIS_OPEN_END = 1520
private const val LEAVE = 2750
private const val EXIT = 380

/** The symbol's blocks, measured from the brand kit (branding/symbol.py → TILES), with softened corners. */
private val TILES: List<Path> = listOf(
    listOf(142.4f to 40f, 341.4f to 40f, 286.6f to 235f, 87.6f to 235f),
    listOf(338.4f to 125f, 450.4f to 125f, 419.6f to 235f, 307.6f to 235f),
    listOf(79.4f to 264f, 191.4f to 264f, 160.6f to 374f, 48.6f to 374f),
    listOf(212.4f to 264f, 411.4f to 264f, 356.6f to 459f, 157.6f to 459f),
).map { pts -> rounded(pts.map { Offset(it.first, it.second) }, 9f) }

private fun rounded(pts: List<Offset>, r: Float): Path = Path().apply {
    for (i in pts.indices) {
        val prev = pts[(i + pts.size - 1) % pts.size]
        val p = pts[i]
        val next = pts[(i + 1) % pts.size]
        val a = p + (prev - p).let { it / it.getDistance() * r }
        val b = p + (next - p).let { it / it.getDistance() * r }
        if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
        quadraticTo(p.x, p.y, b.x, b.y)
    }
    close()
}
