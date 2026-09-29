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
import androidx.compose.material.icons.outlined.StayCurrentPortrait
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
import androidx.compose.ui.draw.rotate
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
}

/**
 * While on screen, turns the phone's screen to match [photo]: a vertical photo fills a standing screen,
 * a horizontal one a lying screen. Off (or in Auto) it leaves the menu's choice alone.
 */
@Composable
fun FollowPhotoOrientation(app: TetherApp, photo: com.example.core.data.Photo?, enabled: Boolean, base: String) {
    androidx.compose.runtime.LaunchedEffect(photo?.id, photo?.crop, photo?.displayUri, enabled, base) {
        if (!enabled || photo == null) {
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

/** The rotate button: opens the radial menu and remembers the choice for the whole app. */
@Composable
fun RotateButton(app: TetherApp, current: String, tint: Color = Tone.Text, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val turn = ScreenTurn.of(current)
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            if (turn == ScreenTurn.Auto) Icons.Outlined.ScreenRotation else Icons.Outlined.ScreenRotationAlt,
            contentDescription = "Girar a tela: ${turn.label}",
            tint = if (turn == ScreenTurn.Auto) tint else Tone.Accent,
        )
    }
    if (open) {
        val scope = rememberCoroutineScope()
        RotationMenu(turn, onPick = {
            scope.launch { app.settings.set(SettingKeys.screenOrientation, it.id) }
            open = false
        }, onDismiss = { open = false })
    }
}

/**
 * Radial menu: the four ways the phone can sit around a central Auto. Each button shows a phone
 * turned that way, so the choice is made by looking, not reading.
 */
@Composable
fun RotationMenu(current: ScreenTurn, onPick: (ScreenTurn) -> Unit, onDismiss: () -> Unit) {
    val grow = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(300.dp).graphicsLayer { scaleX = grow.value; scaleY = grow.value; alpha = grow.value }
                    .clip(CircleShape).background(Tone.Surface.copy(alpha = 0.96f)).border(1.dp, Tone.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ScreenTurn.entries.forEach { turn ->
                    val radius = 100.dp
                    RadialOption(
                        turn = turn,
                        selected = turn == current,
                        modifier = Modifier.offset(radius * turn.dx, radius * turn.dy),
                        onClick = { onPick(turn) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RadialOption(turn: ScreenTurn, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(if (selected) Tone.Accent else Tone.SurfaceHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (turn == ScreenTurn.Auto) Icons.Outlined.ScreenRotation else Icons.Outlined.StayCurrentPortrait,
                contentDescription = turn.label,
                tint = if (selected) Tone.OnAccent else Tone.Text,
                modifier = Modifier.size(30.dp).rotate(turn.angle),
            )
        }
        Text(
            when (turn) {
                ScreenTurn.Auto -> "Auto"
                ScreenTurn.Portrait -> "Em pé"
                ScreenTurn.Right -> "Direita"
                ScreenTurn.UpsideDown -> "Invertido"
                ScreenTurn.Left -> "Esquerda"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Tone.Accent else Tone.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
