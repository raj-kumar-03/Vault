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

private val AranDarkColorScheme = darkColorScheme(
    primary = AranPrimaryDark,
    onPrimary = AranOnPrimaryDark,
    primaryContainer = AranPrimaryMutedDark,
    onPrimaryContainer = AranTextPrimaryDark,
    secondary = AranPrimaryDark,
    onSecondary = AranOnPrimaryDark,
    secondaryContainer = AranSurfaceElevatedDark,
    onSecondaryContainer = AranTextPrimaryDark,
    tertiary = AranInfoDark,
    onTertiary = AranOnPrimaryDark,
    background = AranBackgroundDark,
    onBackground = AranTextPrimaryDark,
    surface = AranSurfaceDark,
    onSurface = AranTextPrimaryDark,
    surfaceVariant = AranSurfaceElevatedDark,
    onSurfaceVariant = AranTextSecondaryDark,
    surfaceContainerLowest = AranBackgroundDark,
    surfaceContainerLow = AranSurfaceDark,
    surfaceContainer = AranSurfaceDark,
    surfaceContainerHigh = AranSurfaceElevatedDark,
    surfaceContainerHighest = AranSurfaceHighestDark,
    outline = AranOutlineDark,
    outlineVariant = AranOutlineSubtleDark,
    error = AranDangerDark,
    onError = AranOnDangerDark,
    errorContainer = AranDangerContainerDark,
    onErrorContainer = AranDangerDark,
)

private val AranLightColorScheme = lightColorScheme(
    primary = AranPrimaryLight,
    onPrimary = AranOnPrimaryLight,
    primaryContainer = AranPrimaryMutedLight,
    onPrimaryContainer = AranTextPrimaryLight,
    secondary = AranPrimaryLight,
    onSecondary = AranOnPrimaryLight,
    secondaryContainer = AranSurfaceElevatedLight,
    onSecondaryContainer = AranTextPrimaryLight,
    tertiary = AranInfoLight,
    onTertiary = AranOnPrimaryLight,
    background = AranBackgroundLight,
    onBackground = AranTextPrimaryLight,
    surface = AranSurfaceLight,
    onSurface = AranTextPrimaryLight,
    surfaceVariant = AranSurfaceElevatedLight,
    onSurfaceVariant = AranTextSecondaryLight,
    surfaceContainerLowest = AranSurfaceLight,
    surfaceContainerLow = AranBackgroundLight,
    surfaceContainer = AranSurfaceLight,
    surfaceContainerHigh = AranSurfaceElevatedLight,
    surfaceContainerHighest = AranSurfaceHighestLight,
    outline = AranOutlineLight,
    outlineVariant = AranOutlineSubtleLight,
    error = AranDangerLight,
    onError = AranOnDangerLight,
    errorContainer = AranDangerContainerLight,
    onErrorContainer = AranDangerLight,
)

val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
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

    val colorScheme = if (darkTheme) AranDarkColorScheme else AranLightColorScheme
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
