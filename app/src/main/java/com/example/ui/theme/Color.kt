package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Aran Dark Charcoal & Vibrant Lime Palette (derived from Reference UI)
val AranBackgroundDark = Color(0xFF1E2221)
val AranSurfaceDark = Color(0xFF282C2B)
val AranSurfaceElevatedDark = Color(0xFF323736)
val AranSurfaceHighestDark = Color(0xFF3C4240)
val AranPrimaryDark = Color(0xFF90F726)
val AranOnPrimaryDark = Color(0xFF111B07)
val AranPrimaryMutedDark = Color(0xFF2A3B1E)
val AranTextPrimaryDark = Color(0xFFF4F6F5)
val AranTextSecondaryDark = Color(0xFF9CA3A0)
val AranOutlineDark = Color(0xFF424846)
val AranOutlineSubtleDark = Color(0xFF313735)
val AranDangerDark = Color(0xFFF26D6D)
val AranOnDangerDark = Color(0xFF2F0A0C)
val AranDangerContainerDark = Color(0xFF3F2324)
val AranWarningDark = Color(0xFFF3BA45)
val AranWarningContainerDark = Color(0xFF3D321B)
val AranInfoDark = Color(0xFF71BDF0)
val AranInfoContainerDark = Color(0xFF1C303D)

// Aran Light Theme Parity (crisp mineral paper + high-contrast forest-lime accent)
val AranBackgroundLight = Color(0xFFF4F6F5)
val AranSurfaceLight = Color(0xFFFFFFFF)
val AranSurfaceElevatedLight = Color(0xFFEAEEEC)
val AranSurfaceHighestLight = Color(0xFFDFE5E2)
val AranPrimaryLight = Color(0xFF2F7E09)
val AranOnPrimaryLight = Color(0xFFFFFFFF)
val AranPrimaryMutedLight = Color(0xFFE0F4D4)
val AranTextPrimaryLight = Color(0xFF161A19)
val AranTextSecondaryLight = Color(0xFF565E5B)
val AranOutlineLight = Color(0xFFD1D8D5)
val AranOutlineSubtleLight = Color(0xFFE4E9E7)
val AranDangerLight = Color(0xFFC5393E)
val AranOnDangerLight = Color(0xFFFFFFFF)
val AranDangerContainerLight = Color(0xFFFCE8E8)
val AranWarningLight = Color(0xFF9D6B0B)
val AranWarningContainerLight = Color(0xFFFCF3DC)
val AranInfoLight = Color(0xFF1B6CA0)
val AranInfoContainerLight = Color(0xFFE3F1FA)

@Immutable
data class VaultSemanticColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighest: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val outlineSubtle: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val isDark: Boolean,
)

val DarkVaultSemanticColors = VaultSemanticColors(
    background = AranBackgroundDark,
    surface = AranSurfaceDark,
    surfaceElevated = AranSurfaceElevatedDark,
    surfaceHighest = AranSurfaceHighestDark,
    primary = AranPrimaryDark,
    onPrimary = AranOnPrimaryDark,
    primaryMuted = AranPrimaryMutedDark,
    textPrimary = AranTextPrimaryDark,
    textSecondary = AranTextSecondaryDark,
    outline = AranOutlineDark,
    outlineSubtle = AranOutlineSubtleDark,
    danger = AranDangerDark,
    onDanger = AranOnDangerDark,
    dangerContainer = AranDangerContainerDark,
    warning = AranWarningDark,
    warningContainer = AranWarningContainerDark,
    info = AranInfoDark,
    infoContainer = AranInfoContainerDark,
    isDark = true,
)

val LightVaultSemanticColors = VaultSemanticColors(
    background = AranBackgroundLight,
    surface = AranSurfaceLight,
    surfaceElevated = AranSurfaceElevatedLight,
    surfaceHighest = AranSurfaceHighestLight,
    primary = AranPrimaryLight,
    onPrimary = AranOnPrimaryLight,
    primaryMuted = AranPrimaryMutedLight,
    textPrimary = AranTextPrimaryLight,
    textSecondary = AranTextSecondaryLight,
    outline = AranOutlineLight,
    outlineSubtle = AranOutlineSubtleLight,
    danger = AranDangerLight,
    onDanger = AranOnDangerLight,
    dangerContainer = AranDangerContainerLight,
    warning = AranWarningLight,
    warningContainer = AranWarningContainerLight,
    info = AranInfoLight,
    infoContainer = AranInfoContainerLight,
    isDark = false,
)

val LocalVaultColors = staticCompositionLocalOf { DarkVaultSemanticColors }
