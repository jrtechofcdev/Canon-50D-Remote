package com.example.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Space
import com.example.ui.theme.TileShape
import com.example.ui.theme.Tone

/*
 * The app's one set of building blocks (DESIGN.md → Componentes). Every screen takes its buttons, chips,
 * cards, warnings, headers and tiles from here, so the same thing always looks and behaves the same.
 */

enum class JrButtonKind { Primary, Secondary, Ghost, Danger }

/**
 * Primary is mint and there is one per area: the thing to do here. Secondary is a tonal grey for the
 * other actions, Ghost is text only (Cancelar, Fechar) and Danger is red text for deleting or stopping.
 */
@Composable
fun JrButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: JrButtonKind = JrButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val (container, content) = when (kind) {
        JrButtonKind.Primary -> Tone.Accent to Tone.OnAccent
        JrButtonKind.Secondary -> Tone.SurfaceHigh to Tone.Text
        JrButtonKind.Ghost -> Color.Transparent to Tone.Text
        JrButtonKind.Danger -> Color.Transparent to Tone.Error
    }
    val flat = kind == JrButtonKind.Ghost || kind == JrButtonKind.Danger
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (flat) Color.Transparent else Tone.SurfaceHigh,
            disabledContentColor = Tone.TextMuted,
        ),
        contentPadding = if (flat) PaddingValues(horizontal = Space.m) else PaddingValues(horizontal = Space.l + Space.xs),
        modifier = modifier.heightIn(min = 48.dp).pressScale(0.98f),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The slanted block of the Jr_Tech_OFC symbol as a state mark: mint for live or chosen, amber while
 * something recovers, red for a problem, grey for off. It always marks a real state, never decoration.
 */
@Composable
fun StatusMark(color: Color, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    Box(modifier.size(width = height * 0.72f, height = height).clip(TileShape(slant = 0.3f, corner = 0.14f)).background(color))
}

/**
 * The one way the app shows "chosen": a raised background, light text and the mint block on the left.
 * Filters, presets, ratios and options all use it, so selection reads the same everywhere.
 */
@Composable
fun JrChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .then(if (selected) Modifier.background(Tone.SurfaceHigh) else Modifier.border(1.dp, Tone.Line, MaterialTheme.shapes.small))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = Space.m)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            StatusMark(Tone.Accent, height = 12.dp)
            Spacer(Modifier.width(Space.s))
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = if (selected) Tone.Text else Tone.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Tone.Text else Tone.TextMuted,
            maxLines = 1,
        )
        count?.let {
            Spacer(Modifier.width(6.dp))
            Text(it.toString(), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Tone.TextMuted)
        }
    }
}

/** [JrChip] for use over a photo or the live view: on the photo veil, with the same mint block when chosen. */
@Composable
fun PhotoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    Box(Modifier.heightIn(min = 48.dp).then(modifier), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(Tone.Scrim)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 36.dp)
                .padding(horizontal = if (compact) Space.s else Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                StatusMark(Tone.Accent, height = 12.dp)
                Spacer(Modifier.width(6.dp))
            }
            icon?.let {
                Icon(it, contentDescription = null, tint = Tone.OnPhoto, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Tone.OnPhoto else Tone.OnPhoto.copy(alpha = 0.72f), maxLines = 1)
        }
    }
}

/** Fades the edges of a sideways-scrolling row where there is more to see, instead of cutting a word in half. */
fun Modifier.edgeFade(state: ScrollState, color: Color, width: Dp = 32.dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    if (state.canScrollForward) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, color), startX = size.width - w, endX = size.width),
            topLeft = Offset(size.width - w, 0f),
            size = Size(w, size.height),
        )
    }
    if (state.canScrollBackward) {
        drawRect(Brush.horizontalGradient(listOf(color, Color.Transparent), startX = 0f, endX = w), size = Size(w, size.height))
    }
}

/** A row of chips that slides sideways, with faded edges showing there is more. */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    background: Color = Tone.Background,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.l),
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().edgeFade(scroll, background).horizontalScroll(scroll).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A card: one surface step up from the screen, 8 dp corners, no border and no shadow. */
@Composable
fun JrCard(modifier: Modifier = Modifier, padding: Dp = Space.l, gap: Dp = Space.m, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Tone.Surface).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

enum class BannerKind { Info, Warning, Error }

/** A notice that stays on screen while it is true. The block on the left says how serious it is. */
@Composable
fun JrBanner(
    kind: BannerKind,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val mark = when (kind) {
        BannerKind.Info -> Tone.Info
        BannerKind.Warning -> Tone.Warning
        BannerKind.Error -> Tone.Error
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Tone.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.l, vertical = Space.m),
    ) {
        StatusMark(mark, Modifier.padding(top = 3.dp), height = 14.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Tone.Text)
            text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted) }
            actions?.let {
                Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s), content = it)
            }
        }
    }
}

/**
 * A screen's top row: back, a title that is never cut short by the buttons, and at most two actions
 * (the rest go to a "Mais" menu inside [actions]).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    count: Int? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack == null) Space.l else Space.xs, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar", tint = Tone.Text) } }
        Column(Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else Space.xs)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Tone.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                count?.let {
                    Spacer(Modifier.width(Space.s))
                    Text(it.toString(), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Tone.TextMuted, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        actions()
    }
}

/** The title row of a bottom sheet: the same size in every sheet, with an optional line under it and a close button. */
@Composable
fun SheetHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClose: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Tone.Text)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted) }
        }
        onClose?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Close, contentDescription = "Fechar", tint = Tone.TextMuted) } }
    }
}

/**
 * A tool: a 24 dp icon in a 52 dp square with its name under it (up to two lines). The icon turns
 * mint only while the tool is on ([active]); names stay neutral.
 */
@Composable
fun JrTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    value: String? = null,
    box: Dp = 52.dp,
    mark: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .widthIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = Space.xs)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(box).clip(MaterialTheme.shapes.small).background(Tone.SurfaceHigh).pressScale(),
            contentAlignment = Alignment.Center,
        ) {
            if (mark != null) mark() else Icon(icon, contentDescription = null, tint = if (active) Tone.Accent else Tone.Text, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        value?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A round button drawn over a photo: a veil behind a white icon, mint only while [active]. */
@Composable
fun PhotoButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Tone.Scrim)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (active) Tone.Accent else Tone.OnPhoto, modifier = Modifier.size(size * 0.48f))
    }
}

/** An empty screen: the symbol in outline, what is missing, why, and the one thing to do about it. */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, text: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        BrandLoader(64.dp, still = true)
        Text(title, style = MaterialTheme.typography.titleMedium, color = Tone.Text, textAlign = TextAlign.Center)
        text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tone.TextMuted, textAlign = TextAlign.Center) }
        action?.let { Box(Modifier.padding(top = Space.xs)) { it() } }
    }
}
