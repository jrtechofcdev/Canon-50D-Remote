package com.example.ui.components

import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How far the phone is turned away from the way the screen is drawn, in quarter turns clockwise:
 * 0, 90, 180 or 270. With the screen fixed in one position (Ajustes → Tela), holding the phone sideways
 * gives 90 or 270 while everything on screen stays where it was. [enabled] off always gives 0.
 */
@Composable
fun rememberPhoneTurn(enabled: Boolean): Int {
    val context = LocalContext.current
    val view = LocalView.current
    var turn by remember { mutableIntStateOf(0) }
    DisposableEffect(enabled) {
        if (!enabled) {
            turn = 0
            return@DisposableEffect onDispose { }
        }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return // flat on a table: keep what it was
                val screen = when (view.display?.rotation) {
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }
                val raw = (degrees + screen) % 360
                // Only a clear quarter turn counts (±30°), so a phone held a little crooked doesn't flip.
                val nearest = ((raw / 90f).roundToInt() % 4) * 90
                val off = abs(((raw - nearest + 540) % 360) - 180)
                if (off <= 30 && nearest != turn) turn = nearest
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return turn
}

/**
 * Shows [content] turned by [turn] degrees against the screen (the picture follows the phone, the
 * screen doesn't). On a quarter turn the content gets the screen's width as its height and the other way
 * round, so a lying photo fills a standing screen held sideways. The turn eases in with a spring.
 */
@Composable
fun PhoneTurned(turn: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val angle = remember { Animatable(-turn.toFloat()) }
    LaunchedEffect(turn) {
        // The short way round: from 270 to 0 turns a quarter, not three.
        var target = -turn.toFloat()
        while (target - angle.value > 180f) target -= 360f
        while (target - angle.value < -180f) target += 360f
        angle.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 300f))
    }
    val sideways = turn == 90 || turn == 270
    Layout(content, modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val inner = if (sideways) Constraints.fixed(h, w) else Constraints.fixed(w, h)
        val placeables = measurables.map { it.measure(inner) }
        layout(w, h) {
            placeables.forEach { p ->
                p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) { rotationZ = angle.value }
            }
        }
    }
}
