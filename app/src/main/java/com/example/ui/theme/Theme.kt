package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB5A5), // Lighter peach/terracotta for dark theme
    onPrimary = Color(0xFF561F10),
    primaryContainer = Color(0xFF723524),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE7BDB4),
    onSecondary = Color(0xFF442A24),
    secondaryContainer = Color(0xFF5D3F38),
    onSecondaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF1A1110), // Elegant dark photographic terracotta
    onBackground = Color(0xFFF1DFDB),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DFDB),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    outline = Color(0xFFA08D8A),
    error = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = TerracottaOnPrimary,
    primaryContainer = TerracottaPrimaryContainer,
    onPrimaryContainer = TerracottaOnPrimaryContainer,
    secondary = TerracottaSecondary,
    onSecondary = TerracottaOnSecondary,
    secondaryContainer = TerracottaSecondaryContainer,
    onSecondaryContainer = TerracottaOnSecondaryContainer,
    background = TerracottaBackground,
    onBackground = TerracottaOnBackground,
    surface = TerracottaSurface,
    onSurface = TerracottaOnSurface,
    surfaceVariant = TerracottaSurfaceVariant,
    onSurfaceVariant = TerracottaOnSurfaceVariant,
    outline = TerracottaOutline,
    error = TerracottaError
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic system color by default to guarantee the Bold Terracotta theme shows
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
