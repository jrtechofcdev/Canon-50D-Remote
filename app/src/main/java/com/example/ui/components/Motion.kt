package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch

/**
 * The app's motion, in one place so every screen moves the same way. Each animation answers to
 * something that happened: a tap, a new photo, a number changing, a screen opening. Nothing loops.
 */
object Motion {
    /** Material's "emphasized" curve: quick start, long settle. For things arriving. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SHORT = 160
    const val MEDIUM = 280
    const val LONG = 420

    /** Routes of the tabs: moving between them is a cross-fade, not a push. */
    var tabRoutes: Set<String> = emptySet()

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.between(): Pair<String?, String?> =
        initialState.destination.route to targetState.destination.route

    private fun isTabSwitch(from: String?, to: String?) = from in tabRoutes && to in tabRoutes

    /** A screen opening: from the side, or growing out of the grid for a photo. */
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val (from, to) = between()
        when {
            isTabSwitch(from, to) -> fadeIn(tween(MEDIUM, delayMillis = 60, easing = LinearOutSlowInEasing)) +
                scaleIn(tween(MEDIUM, delayMillis = 60, easing = Emphasized), initialScale = 0.985f)
            to?.startsWith("photo/") == true -> fadeIn(tween(MEDIUM)) + scaleIn(tween(LONG, easing = Emphasized), initialScale = 0.9f)
            else -> fadeIn(tween(MEDIUM)) + slideInHorizontally(tween(LONG, easing = Emphasized)) { it / 5 }
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val (from, to) = between()
        when {
            isTabSwitch(from, to) -> fadeOut(tween(90))
            else -> fadeOut(tween(SHORT)) + slideOutHorizontally(tween(LONG, easing = Emphasized)) { -it / 12 }
        }
    }

    /** Going back: the screen underneath slides back into place. */
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(MEDIUM)) + slideInHorizontally(tween(LONG, easing = Emphasized)) { -it / 12 }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val from = initialState.destination.route
        if (from?.startsWith("photo/") == true) fadeOut(tween(SHORT)) + scaleOut(tween(MEDIUM, easing = Emphasized), targetScale = 0.92f)
        else fadeOut(tween(SHORT)) + slideOutHorizontally(tween(MEDIUM, easing = Emphasized)) { it / 5 }
    }
}

/**
 * Sinks a little while pressed and springs back on release, so a tap is felt on screen. It only
 * watches the touch: the click itself stays with the element's own clickable.
 */
fun Modifier.pressScale(pressed: Float = 0.94f): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (down) pressed else 1f,
        spring(dampingRatio = if (down) 1f else 0.45f, stiffness = if (down) 1400f else 500f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                down = true
                waitForUpOrCancellation()
                down = false
            }
        }
}

/**
 * A shutter blink when [key] changes (a new photo landed): a quick white flash that fades, and the
 * picture settling from slightly larger. The first value only shows, it doesn't blink.
 */
fun Modifier.shutterFlash(key: Any?): Modifier = composed {
    val flash = remember { Animatable(0f) }
    val settle = remember { Animatable(1f) }
    val first = remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first.value) { first.value = false; return@LaunchedEffect }
        flash.snapTo(0.55f)
        settle.snapTo(1.04f)
        kotlinx.coroutines.coroutineScope {
            launch { flash.animateTo(0f, tween(Motion.LONG, easing = LinearOutSlowInEasing)) }
            launch { settle.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f)) }
        }
    }
    graphicsLayer { scaleX = settle.value; scaleY = settle.value }
        .drawWithContent {
            drawContent()
            if (flash.value > 0f) drawRect(Color.White.copy(alpha = flash.value))
        }
}

/** Text that rolls to its new value, like a counter: up when it grows, down when it shrinks. */
@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val up = (targetState.filter(Char::isDigit).toLongOrNull() ?: 0) >= (initialState.filter(Char::isDigit).toLongOrNull() ?: 0)
            (slideInVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { if (up) it else -it } + fadeIn(tween(Motion.SHORT))) togetherWith
                (slideOutVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { if (up) -it else it } + fadeOut(tween(Motion.SHORT)))
        },
        label = "rolling",
        modifier = modifier,
    ) { Text(it, style = style, color = color) }
}

/** Fades and lifts in once, [index] beats after the screen opens: lists arrive in order, not all at once. */
fun Modifier.arrive(index: Int, step: Int = 40): Modifier = composed {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(8) * step).toLong())
        t.animateTo(1f, tween(Motion.MEDIUM + 60, easing = Motion.Emphasized))
    }
    graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * 18f * density
    }
}
