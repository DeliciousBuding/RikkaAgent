package io.rikka.agent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Chat font family for message body text. */
val LocalChatFontFamily = staticCompositionLocalOf { FontFamily.Default }

/** Font size multiplier for chat content (0.8 .. 1.5). */
val LocalFontSizeRatio = staticCompositionLocalOf { 1.0f }

// ── Theme Mode ─────────────────────────────────────────────────────────────────

enum class ThemeMode { Light, Dark, Amoled, System }

// ── Typography ─────────────────────────────────────────────────────────────────

val AppTypography = MaterialTheme.typography

// ── Theme Composable ───────────────────────────────────────────────────────────

/**
 * RikkaAgent full Material 3 theme with:
 * - Material Expressive motion scheme
 * - Dynamic color (Material You, Android 12+)
 * - Preset themes (Sakura/Ocean/Spring/Autumn/Black)
 * - Light / Dark / AMOLED dark modes
 * - 50 semantic extended colors via [MaterialTheme.extendColors]
 * - Status bar icon color sync
 */
@Composable
fun RikkaAgentTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    presetThemeId: String = "sakura",
    chatFont: ChatFont = ChatFont.Default,
    fontSizeRatio: Float = 1.0f,
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
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (isAmoled && darkTheme) {
                baseScheme.copy(
                    background = androidx.compose.ui.graphics.Color(0xFF000000),
                    surface = androidx.compose.ui.graphics.Color(0xFF000000),
                )
            } else {
                baseScheme
            }
        }
        else -> {
            val preset = PresetTheme.findById(presetThemeId)
            preset.getColorScheme(dark = darkTheme, amoled = isAmoled)
        }
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
        LocalChatFontFamily provides chatFont.fontFamily,
        LocalFontSizeRatio provides fontSizeRatio.coerceIn(0.8f, 1.5f),
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
