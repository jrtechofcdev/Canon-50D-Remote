package com.example.ui.components

import android.content.pm.ActivityInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * How the whole app sits on the screen. [angle] is where the top of the phone points (0 = up,
 * 90 = right…), used to draw each option; [dx]/[dy] place it around the radial menu.
 */
enum class ScreenTurn(val id: String, val label: String, val orientation: Int, val angle: Float, val dx: Int, val dy: Int) {
    // Follows the phone's sensor even with Android's rotation lock on: "Auto" here always turns.
    Auto("auto", "Automático", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, 0f, 0, 0),
    Portrait("portrait", "Em pé", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, 0f, 0, -1),
    Right("landscape_right", "Deitado, topo à direita", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, 90f, 1, 0),
    UpsideDown("reverse_portrait", "De cabeça para baixo", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, 180f, 0, 1),
    Left("landscape_left", "Deitado, topo à esquerda", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, -90f, -1, 0),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Portrait

        /**
         * The fixed position for a photo's shape, keeping the side the photographer chose: a vertical
         * photo stands up (upside down if that was the choice), a horizontal one lies on the chosen side.
         * Null in Auto: then the phone itself decides.
         */
        fun forPhoto(base: ScreenTurn, portrait: Boolean): Int? = when {
            base == Auto -> null
            portrait -> if (base == UpsideDown) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> if (base == Right) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}

/**
 * A position asked by the screen on show (the viewer or client mode turning with the photo),
 * above the one chosen in the menu. Null: the menu's choice applies.
 */
object ScreenOverride {
    val orientation = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    /** Counts the picks made on the dial: a pick while a photo is shown wins over turning with it. */
    val picks = kotlinx.coroutines.flow.MutableStateFlow(0)
}

/**
 * While on screen, turns the phone's screen to match [photo]: a vertical photo fills a standing screen,
 * a horizontal one a lying screen. Off (or in Auto) it leaves the menu's choice alone.
 */
@Composable
fun FollowPhotoOrientation(app: TetherApp, photo: com.example.core.data.Photo?, enabled: Boolean, base: String) {
    // A turn chosen on the dial holds until the next photo; the photo's own shape decides again after that.
    val picks by ScreenOverride.picks.collectAsState()
    val picksAtPhoto = remember(photo?.id) { ScreenOverride.picks.value }
    androidx.compose.runtime.LaunchedEffect(photo?.id, photo?.crop, photo?.displayUri, enabled, base, picks) {
        if (!enabled || photo == null || picks != picksAtPhoto) {
            ScreenOverride.orientation.value = null
            return@LaunchedEffect
        }
        val portrait = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.core.render.PhotoShape.isPortrait(app, photo)
        } ?: return@LaunchedEffect
        ScreenOverride.orientation.value = ScreenTurn.forPhoto(ScreenTurn.of(base), portrait)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ScreenOverride.orientation.value = null } }
}

/**
 * The rotate button: opens the position dial and remembers the choice for the whole app. The choice is
 * saved in a scope that outlives the dial (closing it used to cancel the save, so nothing turned).
 */
@Composable
fun RotateButton(app: TetherApp, current: String, tint: Color = Tone.Text, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val turn = ScreenTurn.of(current)
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            if (turn == ScreenTurn.Auto) Icons.Outlined.ScreenRotation else Icons.Outlined.ScreenRotationAlt,
            contentDescription = "Girar a tela: ${turn.label}",
            tint = if (turn == ScreenTurn.Auto) tint else Tone.Accent,
        )
    }
    if (open) {
        RotationMenu(turn, onPick = {
            ScreenOverride.picks.value += 1
            scope.launch { app.settings.set(SettingKeys.screenOrientation, it.id) }
        }, onDismiss = { open = false })
    }
}

/**
 * The position dial, drawn like a camera's mode dial: a ring with the four ways the phone can sit, and
 * a phone in the middle that turns with a spring to the one chosen. Tap a position, or drag around the
 * ring (it clicks into each quarter turn); "Automático" below follows the phone's sensor.
 */
@Composable
fun RotationMenu(current: ScreenTurn, onPick: (ScreenTurn) -> Unit, onDismiss: () -> Unit, inline: Boolean = false) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var chosen by remember { mutableStateOf(current) }
    var closing by remember { mutableStateOf(false) }
    val shown = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    // The phone's angle never wraps around: it always takes the short way to the next position.
    val phone = remember { Animatable(if (current == ScreenTurn.Auto) 0f else current.angle) }
    val marker = remember { Animatable(if (current == ScreenTurn.Auto) 0f else current.angle) }
    LaunchedEffect(Unit) {
        launch { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f)) }
        ring.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }

    fun close(then: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        scope.launch {
            shown.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
            then?.invoke()
            onDismiss()
        }
    }

    fun pick(turn: ScreenTurn) {
        if (closing) return
        chosen = turn
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val target = if (turn == ScreenTurn.Auto) 0f else nearestTurn(phone.value, turn.angle)
            launch { marker.animateTo(nearestTurn(marker.value, target), spring(dampingRatio = 0.7f, stiffness = 260f)) }
            phone.animateTo(target, spring(dampingRatio = 0.55f, stiffness = 210f))
            close { onPick(turn) }
        }
    }

    val content = @Composable {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            contentAlignment = Alignment.Center,
        ) {
            val lying = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val panel = Modifier.graphicsLayer {
                    val k = 0.88f + 0.12f * shown.value
                    scaleX = k; scaleY = k
                }.clip(RoundedCornerShape(28.dp)).background(Tone.Surface).border(1.dp, Tone.Line, RoundedCornerShape(28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(top = 20.dp, bottom = 18.dp, start = 8.dp, end = 8.dp)
            val header = @Composable {
                Text("Posição da tela", style = MaterialTheme.typography.titleMedium, color = Tone.Text)
                AnimatedContent(
                    targetState = chosen,
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 2 }) },
                    label = "turn",
                ) { t ->
                    Text(
                        if (t == ScreenTurn.Auto) "Segue o sensor do celular" else t.label,
                        style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted,
                    )
                }
            }
            val dial = @Composable {
                Box(
                    Modifier.size(DIAL).pointerInput(Unit) {
                        var detent = Int.MIN_VALUE
                        detectDragGestures(
                            onDragStart = { detent = Int.MIN_VALUE },
                            onDragEnd = {
                                val q = Math.floorMod(Math.round(phone.value / 90f), 4)
                                pick(listOf(ScreenTurn.Portrait, ScreenTurn.Right, ScreenTurn.UpsideDown, ScreenTurn.Left)[q])
                            },
                        ) { change, _ ->
                            if (closing) return@detectDragGestures
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val d = change.position - c
                            if (d.getDistance() < size.width * 0.12f) return@detectDragGestures
                            val raw = Math.toDegrees(kotlin.math.atan2(d.x.toDouble(), -d.y.toDouble())).toFloat()
                            val angle = nearestTurn(phone.value, raw)
                            scope.launch { phone.snapTo(angle); marker.snapTo(angle) }
                            val q = Math.round(angle / 90f)
                            if (q != detent) {
                                if (detent != Int.MIN_VALUE) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                detent = q
                            }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    DialFace(
                        ring = ring.value,
                        marker = marker.value,
                        phone = phone.value,
                        auto = chosen == ScreenTurn.Auto,
                    )
                    listOf(ScreenTurn.Portrait, ScreenTurn.Right, ScreenTurn.UpsideDown, ScreenTurn.Left).forEachIndexed { i, turn ->
                        val a = Math.toRadians(turn.angle.toDouble())
                        val r = LABEL_RADIUS
                        val on = chosen == turn
                        val tone by animateColorAsState(if (on) Tone.Accent else Tone.TextMuted, tween(220), label = "label")
                        Box(
                            Modifier.offset(r * kotlin.math.sin(a).toFloat(), -r * kotlin.math.cos(a).toFloat())
                                .graphicsLayer {
                                    // Each position arrives a beat after the last, following the ring as it draws.
                                    val t = ((ring.value - i * 0.15f) / 0.4f).coerceIn(0f, 1f)
                                    alpha = t; translationY = (1f - t) * 8f
                                }
                                .size(width = 76.dp, height = 48.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { pick(turn) }.semantics { contentDescription = turn.label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(SHORT[turn] ?: turn.label, style = MaterialTheme.typography.labelLarge, color = tone, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            val footer = @Composable {
                val autoOn = chosen == ScreenTurn.Auto
                val autoBg by animateColorAsState(if (autoOn) Tone.Accent else Tone.SurfaceHigh, tween(220), label = "autoBg")
                val autoFg by animateColorAsState(if (autoOn) Tone.OnAccent else Tone.Text, tween(220), label = "autoFg")
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(autoBg).clickable { pick(ScreenTurn.Auto) }
                        .heightIn(min = 48.dp).padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ScreenRotation, null, tint = autoFg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Automático", style = MaterialTheme.typography.labelLarge, color = autoFg)
                }
                Text(
                    "Toque numa posição ou arraste em volta",
                    style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            // Lying down the panel would be taller than the screen: the dial goes left, the words right.
            if (lying) {
                Row(panel.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    dial()
                    Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        header()
                        Spacer(Modifier.height(18.dp))
                        footer()
                    }
                }
            } else {
                Column(panel, horizontalAlignment = Alignment.CenterHorizontally) {
                    header()
                    Spacer(Modifier.height(10.dp))
                    dial()
                    Spacer(Modifier.height(8.dp))
                    footer()
                }
            }
        }
    }
    // Screenshot tests draw the dial in place; the app shows it over everything.
    if (inline) content() else Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) { content() }
}

/** The ring, its four ticks, the marker on the chosen side and the phone turned to [phone] degrees. */
@Composable
private fun DialFace(ring: Float, marker: Float, phone: Float, auto: Boolean) {
    val line = Tone.Line
    val accent = Tone.Accent
    val text = Tone.Text
    val screen = Tone.SurfaceHigh
    val plate = Tone.Background
    val markerAlpha by animateFloatAsState(if (auto) 0f else 1f, tween(240), label = "marker")
    val autoGlow by animateFloatAsState(if (auto) 1f else 0f, tween(240), label = "auto")
    Canvas(Modifier.size(DIAL)) {
        val c = center
        val r = RING_RADIUS.toPx()
        val stroke = 2.dp.toPx()
        // The dial's plate: a darker disc so the ring and the phone read as one object.
        drawCircle(plate, r + 22.dp.toPx() * ring, c)
        // The ring draws itself in on opening, starting from the top like a dial turning on.
        drawArc(
            color = if (auto) accent.copy(alpha = 0.35f + 0.25f * autoGlow) else line,
            startAngle = -90f, sweepAngle = 360f * ring, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(stroke),
        )
        // Fine ticks every 30°, longer ones on the four positions, like the engraving on a mode dial.
        for (deg in 0 until 360 step 30) {
            if (deg / 360f > ring) continue
            val major = deg % 90 == 0
            val a = Math.toRadians(deg.toDouble())
            val dir = Offset(kotlin.math.sin(a).toFloat(), -kotlin.math.cos(a).toFloat())
            val inner = r - (if (major) 10.dp else 5.dp).toPx()
            drawLine(if (major) text.copy(alpha = 0.7f) else line, c + dir * inner, c + dir * r, strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
        }
        // The marker: a short mint arc riding the ring to the chosen side.
        if (markerAlpha > 0f) {
            drawArc(
                color = accent.copy(alpha = markerAlpha),
                startAngle = marker - 90f - 18f, sweepAngle = 36f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        // The phone, standing for the screen: its top (camera dot and mint edge) points where the app's top will be.
        rotate(phone, c) {
            val w = 54.dp.toPx()
            val h = 96.dp.toPx()
            val tl = Offset(c.x - w / 2, c.y - h / 2)
            val corner = CornerRadius(11.dp.toPx())
            drawRoundRect(screen, tl, Size(w, h), corner)
            drawRoundRect(text, tl, Size(w, h), corner, style = Stroke(2.5.dp.toPx()))
            drawLine(accent, Offset(c.x - w * 0.22f, tl.y + 7.dp.toPx()), Offset(c.x + w * 0.22f, tl.y + 7.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(text.copy(alpha = 0.5f), 2.dp.toPx(), Offset(c.x, tl.y + h - 8.dp.toPx()))
            // A tiny photo on the screen, so the phone reads as "the app" and not a blank shape.
            val ph = Size(w * 0.62f, h * 0.36f)
            drawRoundRect(line, Offset(c.x - ph.width / 2, c.y - ph.height / 2 - 4.dp.toPx()), ph, CornerRadius(3.dp.toPx()))
        }
    }
}

/** [target] moved by whole turns so it is the closest to [from]: 350° next to 0° is -10°. */
internal fun nearestTurn(from: Float, target: Float): Float {
    var t = target
    while (t - from > 180f) t -= 360f
    while (t - from < -180f) t += 360f
    return t
}

private val DIAL = 300.dp
private val RING_RADIUS = 82.dp
private val LABEL_RADIUS = 116.dp
private val SHORT = mapOf(
    ScreenTurn.Portrait to "Em pé",
    ScreenTurn.Right to "Direita",
    ScreenTurn.UpsideDown to "Invertido",
    ScreenTurn.Left to "Esquerda",
)
