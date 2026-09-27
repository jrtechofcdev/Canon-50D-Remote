package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Palette from DESIGN.md: neutral greys so the photo's colour is judged honestly, one amber accent.
class Palette(
    val Background: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val Line: Color,
    val Text: Color,
    val TextMuted: Color,
    val Accent: Color,
    val OnAccent: Color,
    val Error: Color,
)

private val DayPalette = Palette(
    Background = Color(0xFF0E0F10),
    Surface = Color(0xFF17181A),
    SurfaceHigh = Color(0xFF202124),
    Line = Color(0xFF2C2E31),
    Text = Color(0xFFECEDEE),
    TextMuted = Color(0xFF9A9DA2),
    Accent = Color(0xFFF0A43A),
    OnAccent = Color(0xFF1B1204),
    Error = Color(0xFFFF6B6B),
)

/** "Modo escuro total": for churches and night events, nothing on screen brighter than it needs to be. */
private val NightPalette = Palette(
    Background = Color(0xFF000000),
    Surface = Color(0xFF0A0A0B),
    SurfaceHigh = Color(0xFF131315),
    Line = Color(0xFF1E1F21),
    Text = Color(0xFFB4B6B9),
    TextMuted = Color(0xFF6E7176),
    Accent = Color(0xFFB0752A),
    OnAccent = Color(0xFF120B02),
    Error = Color(0xFFC75050),
)

private val LocalPalette = staticCompositionLocalOf { DayPalette }

val Tone: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

private fun colorsFor(p: Palette) = darkColorScheme(
    primary = p.Accent,
    onPrimary = p.OnAccent,
    primaryContainer = p.SurfaceHigh,
    onPrimaryContainer = p.Text,
    secondary = p.Text,
    onSecondary = p.Background,
    secondaryContainer = p.SurfaceHigh,
    onSecondaryContainer = p.Text,
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

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/** Exposure readout, like the 50D's top LCD: large, tabular digits so values don't jump while changing. */
val ReadoutStyle = TextStyle(
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun TetherTheme(night: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (night) NightPalette else DayPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorsFor(palette), typography = typography, shapes = shapes, content = content)
    }
}
