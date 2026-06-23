package io.rikka.agent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.rikka.agent.ui.theme.LocalExtendColors
import io.rikka.agent.ui.theme.darkExtendColors
import io.rikka.agent.ui.theme.lightExtendColors

enum class ThemeMode { Light, Dark, Amoled, System }

private val LightScheme: ColorScheme = lightColorScheme(
  primary = Color(0xFF3491FA),
  onPrimary = Color(0xFFFFFFFF),
  secondary = Color(0xFF3A6EA5),
  onSecondary = Color(0xFFFFFFFF),
  tertiary = Color(0xFF3491FA),
  background = Color(0xFFF8F5F0),
  onBackground = Color(0xFF0A0A0A),
  surface = Color(0xFFFFFFFF),
  onSurface = Color(0xFF0A0A0A),
  surfaceVariant = Color(0xFFF1ECE3),
  onSurfaceVariant = Color(0xFF2B2B2B),
  surfaceBright = Color(0xFFFFFFFF),
  outline = Color(0x332B2B2B),
  outlineVariant = Color(0xFFE5E6EB),
  primaryContainer = Color(0xFFE8F7FF),
  onPrimaryContainer = Color(0xFF001A4D),
  secondaryContainer = Color(0xFFF2F3F5),
  onSecondaryContainer = Color(0xFF1D2129),
  error = Color(0xFFB3261E),
  errorContainer = Color(0xFFFCE4EC),
  onError = Color(0xFFFFFFFF),
  onErrorContainer = Color(0xFF601410),
)

private val DarkScheme: ColorScheme = darkColorScheme(
  primary = Color(0xFF47A8FF),
  onPrimary = Color(0xFF000000),
  secondary = Color(0xFF7FB2E5),
  onSecondary = Color(0xFF07131F),
  tertiary = Color(0xFF47A8FF),
  background = Color(0xFF0B0B0B),
  onBackground = Color(0xFFF5F1E9),
  surface = Color(0xFF141414),
  onSurface = Color(0xFFF5F1E9),
  surfaceVariant = Color(0xFF1C1C1C),
  onSurfaceVariant = Color(0xFFDDDDDD),
  surfaceBright = Color(0xFF1A1A1A),
  outline = Color(0x33DDDDDD),
  outlineVariant = Color(0xFF454545),
  primaryContainer = Color(0xFF001A4D),
  onPrimaryContainer = Color(0xFFE8F7FF),
  secondaryContainer = Color(0xFF272E3B),
  onSecondaryContainer = Color(0xFFF2F3F5),
  error = Color(0xFFCF6679),
  errorContainer = Color(0xFF3B1016),
  onError = Color(0xFF000000),
  onErrorContainer = Color(0xFFF2B8B5),
)

private val AmoledScheme: ColorScheme = darkColorScheme(
  primary = Color(0xFF47A8FF),
  onPrimary = Color(0xFF000000),
  secondary = Color(0xFF7FB2E5),
  onSecondary = Color(0xFF000000),
  tertiary = Color(0xFF47A8FF),
  background = Color(0xFF000000),
  onBackground = Color(0xFFF5F1E9),
  surface = Color(0xFF0A0A0A),
  onSurface = Color(0xFFF5F1E9),
  surfaceVariant = Color(0xFF121212),
  onSurfaceVariant = Color(0xFFDDDDDD),
  surfaceBright = Color(0xFF1A1A1A),
  outline = Color(0x33DDDDDD),
  outlineVariant = Color(0xFF454545),
  primaryContainer = Color(0xFF001A4D),
  onPrimaryContainer = Color(0xFFE8F7FF),
  secondaryContainer = Color(0xFF272E3B),
  onSecondaryContainer = Color(0xFFF2F3F5),
  error = Color(0xFFCF6679),
  errorContainer = Color(0xFF2A0A0E),
  onError = Color(0xFF000000),
  onErrorContainer = Color(0xFFF2B8B5),
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),   // chips, small controls
  small = RoundedCornerShape(6.dp),         // buttons, inputs
  medium = RoundedCornerShape(12.dp),       // cards, dialogs
  large = RoundedCornerShape(16.dp),        // bottom sheets
  extraLarge = RoundedCornerShape(16.dp),   // fullscreen surfaces
)

/** Code font for code blocks and monospace content. */
val LocalCodeFontFamily = staticCompositionLocalOf { FontFamily.Monospace }

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
  bodySmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
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
  labelSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
  ),
)

@Composable
fun RikkaAgentTheme(
  themeMode: ThemeMode = ThemeMode.System,
  content: @Composable () -> Unit,
) {
  val colors = when (themeMode) {
    ThemeMode.Light -> LightScheme
    ThemeMode.Dark -> DarkScheme
    ThemeMode.Amoled -> AmoledScheme
    ThemeMode.System -> if (isSystemInDarkTheme()) DarkScheme else LightScheme
  }
  val extendColors = if (colors.background == Color(0xFF000000) || colors == DarkScheme) darkExtendColors() else lightExtendColors()

  CompositionLocalProvider(
    LocalCodeFontFamily provides FontFamily.Monospace,
    LocalExtendColors provides extendColors,
  ) {
    MaterialTheme(
      colorScheme = colors,
      shapes = AppShapes,
      typography = AppTypography,
      content = content,
    )
  }
}

