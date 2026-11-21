package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SalviaDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF041A24),
    primaryContainer = CosmicVioletContainer,
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = CosmicViolet,
    onSecondary = Color.White,
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = MetallicGold,
    onTertiary = Color(0xFF281C02),
    background = ObsidianBg,
    onBackground = TextLight,
    surface = ObsidianSurface,
    onSurface = TextLight,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextLightSecondary,
    outline = ObsidianBorder,
    error = RoseDanger,
    onError = Color.White,
)

private val SalviaIncognitoColorScheme = darkColorScheme(
    primary = StealthViolet,
    onPrimary = Color(0xFF1F083A),
    primaryContainer = StealthVioletContainer,
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = NebulaMagenta,
    onSecondary = Color(0xFF2A072E),
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = MetallicGold,
    onTertiary = Color(0xFF281C02),
    background = IncognitoBg,
    onBackground = TextLight,
    surface = IncognitoSurface,
    onSurface = TextLight,
    surfaceVariant = IncognitoElevated,
    onSurfaceVariant = Color(0xFFC2B2E8),
    outline = Color(0xFF473073),
    error = RoseDanger,
    onError = Color.White,
)

private val SalviaLightColorScheme = lightColorScheme(
    primary = DeepCosmicPrimary,
    onPrimary = Color.White,
    primaryContainer = DeepCosmicContainer,
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    background = CloudBg,
    onBackground = TextDark,
    surface = CloudSurface,
    onSurface = TextDark,
    surfaceVariant = CloudElevated,
    onSurfaceVariant = TextDarkSecondary,
    outline = Color(0xFFD0CEE6),
    error = RoseDanger,
    onError = Color.White,
)

val NavaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NavaBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isIncognito: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        isIncognito -> SalviaIncognitoColorScheme
        darkTheme -> SalviaDarkColorScheme
        else -> SalviaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NavaTypography,
        shapes = NavaShapes,
        content = content,
    )
}
