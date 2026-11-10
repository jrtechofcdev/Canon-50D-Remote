package com.example.brand

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.Look
import com.example.ui.theme.Palette

/**
 * Revela, "câmara escura" (docs/PLANO_REVELA.md): warm black like a darkroom, paper-coloured text and the
 * safelight orange as the default accent, which the photographer replaces with their brand colour.
 */
object BrandStyle {
    /** Default accent: the darkroom's safelight. */
    val Safelight = Color(0xFFFF7A3D)

    private val darkroom = Palette(
        Background = Color(0xFF110F0D),
        Surface = Color(0xFF1B1815),
        SurfaceHigh = Color(0xFF26221E),
        Line = Color(0xFF36302A),
        Text = Color(0xFFF2EBE0),
        TextMuted = Color(0xFFA89E91),
        Accent = Safelight,
        OnAccent = Color(0xFF1A0D06),
        Info = Color(0xFF8FB7C9),
        Error = Color(0xFFFF4D5E),
        Warning = Color(0xFFE8C26A),
        GradientStart = Safelight,
        GradientEnd = Safelight,
        Scrim = Color(0x99000000),
        OnPhoto = Color(0xFFF7F2EA),
        Red = Color(0xFFE5534B),
        Green = Color(0xFF5BAA62),
        Blue = Color(0xFF5B7FD6),
    )

    /**
     * Red safelight: the whole screen in dim red on black, the way a darkroom keeps your eyes adapted.
     * For churches and night events. Text stays at least 4.5:1 on the black.
     */
    private val safelight = Palette(
        Background = Color(0xFF000000),
        Surface = Color(0xFF0E0303),
        SurfaceHigh = Color(0xFF180606),
        Line = Color(0xFF2E0C0B),
        Text = Color(0xFFFF5A47),
        TextMuted = Color(0xFFD9483A),
        Accent = Color(0xFFFF6A55),
        OnAccent = Color(0xFF000000),
        Info = Color(0xFFD9483A),
        Error = Color(0xFFFF2E2E),
        Warning = Color(0xFFE0703A),
        GradientStart = Color(0xFFFF6A55),
        GradientEnd = Color(0xFFFF6A55),
        Scrim = Color(0xB3000000),
        OnPhoto = Color(0xFFFF6A55),
        Red = Color(0xFFB0352B),
        Green = Color(0xFF8A2A22),
        Blue = Color(0xFF6A201A),
    )

    /** Instrument Sans for the interface: clear, slightly condensed, with tabular figures. */
    val Sans = FontFamily(
        Font(R.font.instrument_sans_regular, FontWeight.Normal),
        Font(R.font.instrument_sans_medium, FontWeight.Medium),
        Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    )

    /** Instrument Serif for large titles: the editorial voice of a printed photo book. */
    val Serif = FontFamily(
        Font(R.font.instrument_serif, FontWeight.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )

    private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = Sans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    private fun serif(size: Int, line: Int) =
        TextStyle(fontFamily = Serif, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)

    private val typography = Typography(
        displayLarge = serif(56, 60),
        displayMedium = serif(44, 48),
        displaySmall = serif(34, 38),
        headlineMedium = serif(28, 32),
        headlineSmall = serif(24, 28),
        titleLarge = sans(20, 26, FontWeight.SemiBold),
        titleMedium = sans(16, 22, FontWeight.SemiBold),
        titleSmall = sans(14, 20, FontWeight.SemiBold),
        bodyLarge = sans(16, 24),
        bodyMedium = sans(14, 20),
        bodySmall = sans(12, 16),
        labelLarge = sans(14, 18, FontWeight.Medium),
        labelMedium = sans(12, 16, FontWeight.Medium),
        labelSmall = sans(11, 14, FontWeight.Medium),
    )

    /** Softer cards than JR, square photos like a print. */
    private val shapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

    val look: Look = Look(
        day = darkroom,
        night = safelight,
        typography = typography,
        shapes = shapes,
        readout = TextStyle(fontFamily = Sans, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        wordmark = TextStyle(fontFamily = Serif, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
        display = serif(34, 38),
    )
}
