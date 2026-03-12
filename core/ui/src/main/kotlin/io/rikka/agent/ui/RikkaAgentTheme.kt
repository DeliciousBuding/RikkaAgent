package io.rikka.agent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightScheme: ColorScheme = lightColorScheme(
  primary = Color(0xFF0A0A0A),
  onPrimary = Color(0xFFF5F1E9),
  secondary = Color(0xFF3A6EA5),
  onSecondary = Color(0xFFFFFFFF),
  background = Color(0xFFF8F5F0),
  onBackground = Color(0xFF0A0A0A),
  surface = Color(0xFFFFFFFF),
  onSurface = Color(0xFF0A0A0A),
  surfaceVariant = Color(0xFFF1ECE3),
  onSurfaceVariant = Color(0xFF2B2B2B),
  outline = Color(0x332B2B2B),
)

private val DarkScheme: ColorScheme = darkColorScheme(
  primary = Color(0xFFF5F1E9),
  onPrimary = Color(0xFF0A0A0A),
  secondary = Color(0xFF7FB2E5),
  onSecondary = Color(0xFF07131F),
  background = Color(0xFF0B0B0B),
  onBackground = Color(0xFFF5F1E9),
  surface = Color(0xFF141414),
  onSurface = Color(0xFFF5F1E9),
  surfaceVariant = Color(0xFF1C1C1C),
  onSurfaceVariant = Color(0xFFDDDDDD),
  outline = Color(0x33DDDDDD),
)

private val AppTypography = Typography(
  bodyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
)

@Composable
fun RikkaAgentTheme(
  useDarkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colors = if (useDarkTheme) DarkScheme else LightScheme
  MaterialTheme(
    colorScheme = colors,
    typography = AppTypography,
    content = content,
  )
}

