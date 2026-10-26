package com.sentinel.adblock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B7A63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F5E5),
    onPrimaryContainer = Color(0xFF00251D),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF0B1512),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1512),
    surfaceVariant = Color(0xFFE8F0EC),
    onSurfaceVariant = Color(0xFF4A5A54),
    outline = Color(0xFFC9D7D1),
    outlineVariant = Color(0xFFDFE9E5),
    error = Color(0xFFB91C1C),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4FE0BE),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0B4C3E),
    onPrimaryContainer = Color(0xFFB7F7E5),
    secondary = Color(0xFF67D4F5),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF0B3D4D),
    onSecondaryContainer = Color(0xFFB6EBFA),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3A2500),
    background = Color(0xFF060B09),
    onBackground = Color(0xFFE6F0EC),
    surface = Color(0xFF0C1512),
    onSurface = Color(0xFFE6F0EC),
    surfaceVariant = Color(0xFF16211D),
    onSurfaceVariant = Color(0xFFA7B7B0),
    outline = Color(0xFF2C3B35),
    outlineVariant = Color(0xFF22302B),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF6B1414),
    onErrorContainer = Color(0xFFFFD9D6),
)

/**
 * Colours the Material scheme does not carry: the brand gradient and semantic
 * accents that stay legible on both backgrounds.
 */
data class SentinelColors(
    val isDark: Boolean,
    val heroStart: Color,
    val heroEnd: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
)

private val LightSentinelColors = SentinelColors(
    isDark = false,
    heroStart = Color(0xFF0E8268),
    heroEnd = Color(0xFF06483A),
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    info = Color(0xFF0369A1),
)

private val DarkSentinelColors = SentinelColors(
    isDark = true,
    heroStart = Color(0xFF0F6B54),
    heroEnd = Color(0xFF07281F),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    info = Color(0xFF38BDF8),
)

val LocalSentinelColors = staticCompositionLocalOf { LightSentinelColors }

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 31.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.7.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SentinelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSentinelColors provides if (darkTheme) DarkSentinelColors else LightSentinelColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Brand and semantic colours for the current theme. */
@Composable
fun sentinelColors(): SentinelColors = LocalSentinelColors.current
