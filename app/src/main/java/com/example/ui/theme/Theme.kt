package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.domain.model.ThemePreference

private val MidnightEmeraldDarkScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldPrimaryMutedDark,
    onPrimaryContainer = EmeraldTextPrimaryDark,
    secondary = EmeraldPrimaryDark,
    onSecondary = EmeraldOnPrimaryDark,
    secondaryContainer = EmeraldSurfaceElevatedDark,
    onSecondaryContainer = EmeraldTextPrimaryDark,
    tertiary = EmeraldInfoDark,
    onTertiary = EmeraldOnPrimaryDark,
    background = EmeraldBackgroundDark,
    onBackground = EmeraldTextPrimaryDark,
    surface = EmeraldSurfaceDark,
    onSurface = EmeraldTextPrimaryDark,
    surfaceVariant = EmeraldSurfaceElevatedDark,
    onSurfaceVariant = EmeraldTextSecondaryDark,
    surfaceContainerLowest = EmeraldBackgroundDark,
    surfaceContainerLow = EmeraldSurfaceDark,
    surfaceContainer = EmeraldSurfaceDark,
    surfaceContainerHigh = EmeraldSurfaceElevatedDark,
    surfaceContainerHighest = EmeraldSurfaceHighestDark,
    outline = EmeraldOutlineDark,
    outlineVariant = EmeraldOutlineSubtleDark,
    error = EmeraldDangerDark,
    onError = EmeraldOnDangerDark,
    errorContainer = EmeraldDangerContainerDark,
    onErrorContainer = EmeraldDangerDark,
)

private val MidnightEmeraldLightScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldPrimaryMutedLight,
    onPrimaryContainer = EmeraldTextPrimaryLight,
    secondary = EmeraldPrimaryLight,
    onSecondary = EmeraldOnPrimaryLight,
    secondaryContainer = EmeraldSurfaceElevatedLight,
    onSecondaryContainer = EmeraldTextPrimaryLight,
    tertiary = EmeraldInfoLight,
    onTertiary = EmeraldOnPrimaryLight,
    background = EmeraldBackgroundLight,
    onBackground = EmeraldTextPrimaryLight,
    surface = EmeraldSurfaceLight,
    onSurface = EmeraldTextPrimaryLight,
    surfaceVariant = EmeraldSurfaceElevatedLight,
    onSurfaceVariant = EmeraldTextSecondaryLight,
    surfaceContainerLowest = EmeraldSurfaceLight,
    surfaceContainerLow = EmeraldBackgroundLight,
    surfaceContainer = EmeraldSurfaceLight,
    surfaceContainerHigh = EmeraldSurfaceElevatedLight,
    surfaceContainerHighest = EmeraldSurfaceHighestLight,
    outline = EmeraldOutlineLight,
    outlineVariant = EmeraldOutlineSubtleLight,
    error = EmeraldDangerLight,
    onError = EmeraldOnDangerLight,
    errorContainer = EmeraldDangerContainerLight,
    onErrorContainer = EmeraldDangerLight,
)

val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object VaultTheme {
    val colors: VaultSemanticColors
        @Composable get() = LocalVaultColors.current
}

@Composable
fun SecureVaultTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) MidnightEmeraldDarkScheme else MidnightEmeraldLightScheme
    val semanticColors = if (darkTheme) DarkVaultSemanticColors else LightVaultSemanticColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalVaultColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VaultTypography,
            shapes = VaultShapes,
            content = content,
        )
    }
}
