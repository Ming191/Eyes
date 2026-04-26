package com.example.eyes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightOnPrimaryContainer,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    secondaryContainer = NightSecondaryContainer,
    onSecondaryContainer = NightOnSecondaryContainer,
    tertiary = NightTertiary,
    onTertiary = NightOnTertiary,
    tertiaryContainer = NightTertiaryContainer,
    onTertiaryContainer = NightOnTertiaryContainer,
    background = NightBackground,
    onBackground = NightOnBackground,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = LagoonPrimary,
    onPrimary = LagoonOnPrimary,
    primaryContainer = LagoonPrimaryContainer,
    onPrimaryContainer = LagoonOnPrimaryContainer,
    secondary = SunSecondary,
    onSecondary = SunOnSecondary,
    secondaryContainer = SunSecondaryContainer,
    onSecondaryContainer = SunOnSecondaryContainer,
    tertiary = FieldTertiary,
    onTertiary = FieldOnTertiary,
    tertiaryContainer = FieldTertiaryContainer,
    onTertiaryContainer = FieldOnTertiaryContainer,
    background = MistBackground,
    onBackground = MistOnBackground,
    surface = MistSurface,
    onSurface = MistOnSurface,
    surfaceVariant = MistSurfaceVariant,
    onSurfaceVariant = MistOnSurfaceVariant,
    outline = MistOutline,
    outlineVariant = MistOutlineVariant
)

@Composable
fun EyesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = EyesShapes,
        content = content
    )
}
