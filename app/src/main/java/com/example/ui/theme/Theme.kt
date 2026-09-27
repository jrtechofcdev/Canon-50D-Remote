package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Palette from DESIGN.md: neutral greys so the photo's colour is judged honestly, one amber accent.
object Tone {
    val Background = Color(0xFF0E0F10)
    val Surface = Color(0xFF17181A)
    val SurfaceHigh = Color(0xFF202124)
    val Line = Color(0xFF2C2E31)
    val Text = Color(0xFFECEDEE)
    val TextMuted = Color(0xFF9A9DA2)
    val Accent = Color(0xFFF0A43A)
    val OnAccent = Color(0xFF1B1204)
    val Error = Color(0xFFFF6B6B)
}

private val colors = darkColorScheme(
    primary = Tone.Accent,
    onPrimary = Tone.OnAccent,
    primaryContainer = Tone.SurfaceHigh,
    onPrimaryContainer = Tone.Text,
    secondary = Tone.Text,
    onSecondary = Tone.Background,
    secondaryContainer = Tone.SurfaceHigh,
    onSecondaryContainer = Tone.Text,
    background = Tone.Background,
    onBackground = Tone.Text,
    surface = Tone.Background,
    onSurface = Tone.Text,
    surfaceVariant = Tone.Surface,
    onSurfaceVariant = Tone.TextMuted,
    surfaceContainerLowest = Tone.Background,
    surfaceContainerLow = Tone.Surface,
    surfaceContainer = Tone.Surface,
    surfaceContainerHigh = Tone.SurfaceHigh,
    surfaceContainerHighest = Tone.SurfaceHigh,
    outline = Tone.Line,
    outlineVariant = Tone.Line,
    error = Tone.Error,
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
fun TetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
