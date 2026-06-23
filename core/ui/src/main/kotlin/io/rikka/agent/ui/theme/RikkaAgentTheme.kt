package io.rikka.agent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

// ── Extend Colors ──────────────────────────────────────────────────────────────

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

/** Code font for code blocks and monospace content. */
val LocalCodeFontFamily = staticCompositionLocalOf { FontFamily.Monospace }

// ── Theme Mode ─────────────────────────────────────────────────────────────────

enum class ThemeMode { Light, Dark, Amoled, System }

// ── AMOLED Background ──────────────────────────────────────────────────────────

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

// ── Static Color Schemes (fallback when dynamic color is off) ──────────────────

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
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFCE4EC),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF601410),
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
    error = Color(0xFFCF6679),
    errorContainer = Color(0xFF3B1016),
    onError = Color(0xFF000000),
    onErrorContainer = Color(0xFFF2B8B5),
)

private val AmoledScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF5F1E9),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF7FB2E5),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F1E9),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF5F1E9),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFDDDDDD),
    outline = Color(0x33DDDDDD),
    error = Color(0xFFCF6679),
    errorContainer = Color(0xFF2A0A0E),
    onError = Color(0xFF000000),
    onErrorContainer = Color(0xFFF2B8B5),
)

// ── Typography ─────────────────────────────────────────────────────────────────

val AppTypography = MaterialTheme.typography

// ── Theme Composable ───────────────────────────────────────────────────────────

/**
 * RikkaAgent full Material 3 theme with:
 * - Material Expressive motion scheme
 * - Dynamic color (Material You, Android 12+)
 * - Light / Dark / AMOLED dark modes
 * - 50 semantic extended colors via [MaterialTheme.extendColors]
 * - Status bar icon color sync
 */
@Composable
fun RikkaAgentTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Amoled -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val isAmoled = themeMode == ThemeMode.Amoled

    // Resolve color scheme
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when {
            darkTheme && isAmoled -> AmoledScheme
            darkTheme -> DarkScheme
            else -> LightScheme
        }
    }

    // Apply AMOLED overrides when using dynamic color + amoled mode
    val colorScheme = if (isAmoled && darkTheme) {
        baseColorScheme.copy(
            background = AMOLED_DARK_BACKGROUND,
            surface = AMOLED_DARK_BACKGROUND,
        )
    } else {
        baseColorScheme
    }

    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    // Sync status bar icon color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalCodeFontFamily provides FontFamily.Monospace,
        LocalOverscrollFactory provides null,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

// ── Extension accessor ─────────────────────────────────────────────────────────

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
