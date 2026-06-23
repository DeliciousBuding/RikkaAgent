package io.rikka.agent.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended semantic color palette aligned with RikkaHub's ExtendColors.
 * 50 slots (red/orange/green/blue/gray × 10 levels each) for precise UI coloring.
 */
data class ExtendColors(
    // Red tones
    val red50: Color, val red100: Color, val red200: Color, val red300: Color,
    val red400: Color, val red500: Color, val red600: Color, val red700: Color,
    val red800: Color, val red900: Color,
    // Orange tones
    val orange50: Color, val orange100: Color, val orange200: Color, val orange300: Color,
    val orange400: Color, val orange500: Color, val orange600: Color, val orange700: Color,
    val orange800: Color, val orange900: Color,
    // Green tones
    val green50: Color, val green100: Color, val green200: Color, val green300: Color,
    val green400: Color, val green500: Color, val green600: Color, val green700: Color,
    val green800: Color, val green900: Color,
    // Blue tones
    val blue50: Color, val blue100: Color, val blue200: Color, val blue300: Color,
    val blue400: Color, val blue500: Color, val blue600: Color, val blue700: Color,
    val blue800: Color, val blue900: Color,
    // Gray tones
    val gray50: Color, val gray100: Color, val gray200: Color, val gray300: Color,
    val gray400: Color, val gray500: Color, val gray600: Color, val gray700: Color,
    val gray800: Color, val gray900: Color,
)

val LocalExtendColors = staticCompositionLocalOf { lightExtendColors() }

fun lightExtendColors() = ExtendColors(
    red50 = Color(0xFFFFEBEE), red100 = Color(0xFFFFCDD2), red200 = Color(0xFFEF9A9A),
    red300 = Color(0xFFE57373), red400 = Color(0xFFEF5350), red500 = Color(0xFFF44336),
    red600 = Color(0xFFE53935), red700 = Color(0xFFD32F2F), red800 = Color(0xFFC62828),
    red900 = Color(0xFFB71C1C),
    orange50 = Color(0xFFFFF3E0), orange100 = Color(0xFFFFE0B2), orange200 = Color(0xFFFFCC80),
    orange300 = Color(0xFFFFB74D), orange400 = Color(0xFFFFA726), orange500 = Color(0xFFFF9800),
    orange600 = Color(0xFFFB8C00), orange700 = Color(0xFFF57C00), orange800 = Color(0xFFEF6C00),
    orange900 = Color(0xFFE65100),
    green50 = Color(0xFFE8F5E9), green100 = Color(0xFFC8E6C9), green200 = Color(0xFFA5D6A7),
    green300 = Color(0xFF81C784), green400 = Color(0xFF66BB6A), green500 = Color(0xFF4CAF50),
    green600 = Color(0xFF43A047), green700 = Color(0xFF388E3C), green800 = Color(0xFF2E7D32),
    green900 = Color(0xFF1B5E20),
    blue50 = Color(0xFFE3F2FD), blue100 = Color(0xFFBBDEFB), blue200 = Color(0xFF90CAF9),
    blue300 = Color(0xFF64B5F6), blue400 = Color(0xFF42A5F5), blue500 = Color(0xFF2196F3),
    blue600 = Color(0xFF1E88E5), blue700 = Color(0xFF1976D2), blue800 = Color(0xFF1565C0),
    blue900 = Color(0xFF0D47A1),
    gray50 = Color(0xFFFAFAFA), gray100 = Color(0xFFF5F5F5), gray200 = Color(0xFFEEEEEE),
    gray300 = Color(0xFFE0E0E0), gray400 = Color(0xFFBDBDBD), gray500 = Color(0xFF9E9E9E),
    gray600 = Color(0xFF757575), gray700 = Color(0xFF616161), gray800 = Color(0xFF424242),
    gray900 = Color(0xFF212121),
)

fun darkExtendColors() = ExtendColors(
    red50 = Color(0xFFFF5252), red100 = Color(0xFFFF5252), red200 = Color(0xFFEF5350),
    red300 = Color(0xFFEF5350), red400 = Color(0xFFE53935), red500 = Color(0xFFE53935),
    red600 = Color(0xFFD32F2F), red700 = Color(0xFFD32F2F), red800 = Color(0xFFC62828),
    red900 = Color(0xFFB71C1C),
    orange50 = Color(0xFFFFA726), orange100 = Color(0xFFFFA726), orange200 = Color(0xFFFF9800),
    orange300 = Color(0xFFFF9800), orange400 = Color(0xFFFB8C00), orange500 = Color(0xFFFB8C00),
    orange600 = Color(0xFFF57C00), orange700 = Color(0xFFF57C00), orange800 = Color(0xFFEF6C00),
    orange900 = Color(0xFFE65100),
    green50 = Color(0xFF66BB6A), green100 = Color(0xFF66BB6A), green200 = Color(0xFF4CAF50),
    green300 = Color(0xFF4CAF50), green400 = Color(0xFF43A047), green500 = Color(0xFF43A047),
    green600 = Color(0xFF388E3C), green700 = Color(0xFF388E3C), green800 = Color(0xFF2E7D32),
    green900 = Color(0xFF1B5E20),
    blue50 = Color(0xFF42A5F5), blue100 = Color(0xFF42A5F5), blue200 = Color(0xFF2196F3),
    blue300 = Color(0xFF2196F3), blue400 = Color(0xFF1E88E5), blue500 = Color(0xFF1E88E5),
    blue600 = Color(0xFF1976D2), blue700 = Color(0xFF1976D2), blue800 = Color(0xFF1565C0),
    blue900 = Color(0xFF0D47A1),
    gray50 = Color(0xFF424242), gray100 = Color(0xFF424242), gray200 = Color(0xFF616161),
    gray300 = Color(0xFF616161), gray400 = Color(0xFF757575), gray500 = Color(0xFF9E9E9E),
    gray600 = Color(0xFFBDBDBD), gray700 = Color(0xFFE0E0E0), gray800 = Color(0xFFEEEEEE),
    gray900 = Color(0xFFF5F5F5),
)
