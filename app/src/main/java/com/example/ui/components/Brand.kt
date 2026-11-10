package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.TileShape
import com.example.ui.theme.Tone
import com.example.ui.theme.WordmarkStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.animateFloat

/** The Jr_Tech_OFC symbol in its brand gradient. */
@Composable
fun BrandSymbol(size: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(R.drawable.ic_brand_symbol),
        contentDescription = null,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.size(size),
    )
}

/** App name set like the brand's wordmark, with the symbol beside it. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandSymbol(28.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("JR C50D Sync", style = WordmarkStyle, color = Tone.Text)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted) }
        }
    }
}

/**
 * State marker in the shape of one of the logo's blocks: mint when live, red on failure, grey otherwise.
 * It only ever marks a real connection state.
 */
@Composable
fun StateTile(color: Color, modifier: Modifier = Modifier) {
    StatusMark(color, modifier, height = 18.dp)
}

/**
 * The four blocks of the symbol (ic_brand_symbol.xml, 500 × 500), each as its corners, in clockwise order:
 * top-left (big), top-right (small), bottom-right (big), bottom-left (small).
 */
private val loaderTiles = listOf(
    floatArrayOf(142.4f, 40f, 341.4f, 40f, 286.6f, 235f, 87.6f, 235f),
    floatArrayOf(338.4f, 125f, 450.4f, 125f, 419.6f, 235f, 307.6f, 235f),
    floatArrayOf(212.4f, 264f, 411.4f, 264f, 356.6f, 459f, 157.6f, 459f),
    floatArrayOf(79.4f, 264f, 191.4f, 264f, 160.6f, 374f, 48.6f, 374f),
)

/** How lit block [k] is, 0 to 1, at [phase] (0..4, one block per unit): lit, then a short fading trail. */
internal fun loaderGlow(phase: Float, k: Int): Float {
    val d = ((phase - k) % 4f + 4f) % 4f
    return when {
        d < 0.8f -> 1f
        d < 1.68f -> 1f - (d - 0.8f) / 0.88f * 0.65f
        d < 2.32f -> 0.35f * (1f - (d - 1.68f) / 0.64f)
        else -> 0f
    }
}

/**
 * The app's loader: the brand symbol in outline with one block lit in mint going round clockwise (an idea
 * of José's). It is the one animation that repeats, because it answers a wait that is still going on, and
 * it is gone as soon as the wait ends. With [progress] (0..1) each finished quarter stays lit and the next
 * one pulses, so a backup shows how much is left without a number. [still] draws it at rest (empty
 * screens, and when the phone's animations are switched off).
 */
@Composable
fun BrandLoader(size: Dp, modifier: Modifier = Modifier, progress: Float? = null, still: Boolean = false) {
    val line = Tone.Line
    val mint = Tone.Accent
    val context = androidx.compose.ui.platform.LocalContext.current
    val animate = !still && androidx.compose.runtime.remember {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) != 0f
    }
    val phase: androidx.compose.runtime.State<Float> = if (animate) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "loader").animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing),
            ),
            label = "phase",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }
    val done = progress?.let { (it.coerceIn(0f, 1f) * 4f).toInt() }
    // What a screen reader says: busy with no end known, or how far along the job is.
    val described = if (still) {
        Modifier
    } else {
        Modifier.semantics {
            if (progress != null) {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            } else {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                contentDescription = "Carregando"
            }
        }
    }
    androidx.compose.foundation.Canvas(
        modifier.size(size).then(described),
    ) {
        val scale = this.size.minDimension / 500f
        val stroke = 22f * scale
        val corner = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(18f * scale)
        loaderTiles.forEachIndexed { k, c ->
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(c[0] * scale, c[1] * scale)
                lineTo(c[2] * scale, c[3] * scale)
                lineTo(c[4] * scale, c[5] * scale)
                lineTo(c[6] * scale, c[7] * scale)
                close()
            }
            val glow = when {
                done != null && k < done -> 1f
                done != null && k == done -> if (animate) (phase.value % 2f).let { if (it < 1f) it else 2f - it } else 1f
                done != null -> 0f
                !animate -> if (k == 2) 1f else 0f
                else -> loaderGlow(phase.value, k)
            }
            if (done != null && k < done) drawPath(path, mint.copy(alpha = 0.22f))
            drawPath(
                path,
                androidx.compose.ui.graphics.lerp(line, mint, glow),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, pathEffect = corner),
            )
        }
    }
}
