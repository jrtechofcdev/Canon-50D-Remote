package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/** Jr_Tech_OFC brand colours (see DESIGN.md and branding/). */
object Brand {
    val Mint = Color(0xFF00FCA8)
    val Cyan = Color(0xFF1CDAFF)
    val Graphite = Color(0xFF292929)
}

class Palette(
    val Background: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val Line: Color,
    val Text: Color,
    val TextMuted: Color,
    /** Mint: the one main action per area, "connected", selection. */
    val Accent: Color,
    val OnAccent: Color,
    /** Cyan: progress and information, never a call to action. */
    val Info: Color,
    val Error: Color,
    val GradientStart: Color,
    val GradientEnd: Color,
)

private val DayPalette = Palette(
    Background = Color(0xFF1F1F1F),
    Surface = Brand.Graphite,
    SurfaceHigh = Color(0xFF333333),
    Line = Color(0xFF3D3D3D),
    Text = Color(0xFFF2F2F2),
    TextMuted = Color(0xFFA6A6A6),
    Accent = Brand.Mint,
    OnAccent = Color(0xFF00261A),
    Info = Brand.Cyan,
    Error = Color(0xFFFF6B6B),
    GradientStart = Brand.Mint,
    GradientEnd = Brand.Cyan,
)

/** "Modo escuro total": for churches and night events, nothing on screen brighter than it needs to be. */
private val NightPalette = Palette(
    Background = Color(0xFF000000),
    Surface = Color(0xFF0D0D0D),
    SurfaceHigh = Color(0xFF171717),
    Line = Color(0xFF222222),
    Text = Color(0xFFB5B5B5),
    TextMuted = Color(0xFF6F6F6F),
    Accent = Color(0xFF00A870),
    OnAccent = Color(0xFF001A11),
    Info = Color(0xFF1290AA),
    Error = Color(0xFFC75050),
    GradientStart = Color(0xFF00A870),
    GradientEnd = Color(0xFF1290AA),
)

private val LocalPalette = staticCompositionLocalOf { DayPalette }

val Tone: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

/** The logo's diagonal mint → cyan. Reserved for the symbol and the shutter button. */
val BrandGradient: Brush
    @Composable @ReadOnlyComposable get() = LocalPalette.current.let {
        Brush.linearGradient(listOf(it.GradientStart, it.GradientEnd), start = Offset.Zero, end = Offset.Infinite)
    }

/**
 * The slanted block from the Jr_Tech_OFC symbol: a parallelogram leaning ~16°, with softened corners.
 * Used for state markers and format tags so the brand shows up in the working UI, not only in the logo.
 */
class TileShape(private val slant: Float = 0.28f, private val corner: Float = 0.12f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val shift = size.height * slant
        val r = minOf(size.width, size.height) * corner
        val pts = listOf(
            Offset(shift, 0f), Offset(size.width, 0f),
            Offset(size.width - shift, size.height), Offset(0f, size.height),
        )
        val path = Path()
        for (i in pts.indices) {
            val prev = pts[(i + pts.size - 1) % pts.size]
            val p = pts[i]
            val next = pts[(i + 1) % pts.size]
            val a = p + (prev - p).let { it / it.getDistance() * r }
            val b = p + (next - p).let { it / it.getDistance() * r }
            if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
            path.quadraticTo(p.x, p.y, b.x, b.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Exo 2, the typeface of the Jr_Tech_OFC wordmark. */
val Exo2 = FontFamily(
    Font(R.font.exo2_regular, FontWeight.Normal),
    Font(R.font.exo2_medium, FontWeight.Medium),
    Font(R.font.exo2_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Exo2, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private val typography = Typography(
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 22),
    bodyMedium = style(14, 20),
    bodySmall = style(12, 16),
    labelLarge = style(14, 18, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium),
)

/** Exposure readout, like the 50D's top LCD: large, tabular digits so values don't jump while changing. */
val ReadoutStyle = TextStyle(
    fontFamily = Exo2,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/** The app name set like the logo's wordmark: Exo 2 with open tracking. */
val WordmarkStyle = TextStyle(
    fontFamily = Exo2,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 1.4.sp,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private fun colorsFor(p: Palette) = darkColorScheme(
    primary = p.Accent,
    onPrimary = p.OnAccent,
    primaryContainer = p.SurfaceHigh,
    onPrimaryContainer = p.Text,
    secondary = p.Info,
    onSecondary = p.Background,
    secondaryContainer = p.SurfaceHigh,
    onSecondaryContainer = p.Text,
    tertiary = p.Info,
    background = p.Background,
    onBackground = p.Text,
    surface = p.Background,
    onSurface = p.Text,
    surfaceVariant = p.Surface,
    onSurfaceVariant = p.TextMuted,
    surfaceContainerLowest = p.Background,
    surfaceContainerLow = p.Surface,
    surfaceContainer = p.Surface,
    surfaceContainerHigh = p.SurfaceHigh,
    surfaceContainerHighest = p.SurfaceHigh,
    outline = p.Line,
    outlineVariant = p.Line,
    error = p.Error,
)

@Composable
fun JrLinkTheme(night: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (night) NightPalette else DayPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorsFor(palette), typography = typography, shapes = shapes) {
            // Text outside a Surface/Scaffold still gets the palette's colour, never Material's black default.
            CompositionLocalProvider(LocalContentColor provides palette.Text, content = content)
        }
    }
}
