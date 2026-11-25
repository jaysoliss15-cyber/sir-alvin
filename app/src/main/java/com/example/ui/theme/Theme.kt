package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ForgeCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1C16),
    onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = ForgeAmber,
    onSecondary = Color(0xFF1D1600),
    secondaryContainer = Color(0xFF33280B),
    onSecondaryContainer = Color(0xFFFFEAB3),
    tertiary = ForgeCyan,
    onTertiary = Color(0xFF001F2A),
    tertiaryContainer = Color(0xFF0D2A38),
    onTertiaryContainer = Color(0xFFC5EEFF),
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioOutline,
    outlineVariant = StudioSurfaceHighlight,
    error = ForgeCrimson,
    onError = Color.White
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ToonForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
