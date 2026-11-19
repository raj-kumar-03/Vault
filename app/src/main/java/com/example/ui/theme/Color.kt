package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Midnight Emerald Dark Tokens (design.md baseline, verified contrast)
val EmeraldBackgroundDark = Color(0xFF0B1210)
val EmeraldSurfaceDark = Color(0xFF111C18)
val EmeraldSurfaceElevatedDark = Color(0xFF17251F)
val EmeraldSurfaceHighestDark = Color(0xFF1E3028)
val EmeraldPrimaryDark = Color(0xFF35C98A)
val EmeraldOnPrimaryDark = Color(0xFF052015)
val EmeraldPrimaryMutedDark = Color(0xFF153D2E)
val EmeraldTextPrimaryDark = Color(0xFFEAF4EE)
val EmeraldTextSecondaryDark = Color(0xFF9CB3A7)
val EmeraldOutlineDark = Color(0xFF263830)
val EmeraldOutlineSubtleDark = Color(0xFF1A2922)
val EmeraldDangerDark = Color(0xFFF07878)
val EmeraldOnDangerDark = Color(0xFF310B0E)
val EmeraldDangerContainerDark = Color(0xFF3A1A1D)
val EmeraldWarningDark = Color(0xFFE7B85C)
val EmeraldWarningContainerDark = Color(0xFF352A14)
val EmeraldInfoDark = Color(0xFF69B7E8)
val EmeraldInfoContainerDark = Color(0xFF142A38)

// Midnight Emerald Light Tokens (calm architectural mineral sage)
val EmeraldBackgroundLight = Color(0xFFF3F7F5)
val EmeraldSurfaceLight = Color(0xFFFFFFFF)
val EmeraldSurfaceElevatedLight = Color(0xFFE9F1ED)
val EmeraldSurfaceHighestLight = Color(0xFFDDE9E3)
val EmeraldPrimaryLight = Color(0xFF147E54)
val EmeraldOnPrimaryLight = Color(0xFFFFFFFF)
val EmeraldPrimaryMutedLight = Color(0xFFD5EFE3)
val EmeraldTextPrimaryLight = Color(0xFF0E1B16)
val EmeraldTextSecondaryLight = Color(0xFF475E53)
val EmeraldOutlineLight = Color(0xFFC8D7CF)
val EmeraldOutlineSubtleLight = Color(0xFFDFE9E3)
val EmeraldDangerLight = Color(0xFFC0393E)
val EmeraldOnDangerLight = Color(0xFFFFFFFF)
val EmeraldDangerContainerLight = Color(0xFFFCE8E8)
val EmeraldWarningLight = Color(0xFF9A6A0E)
val EmeraldWarningContainerLight = Color(0xFFFCF3DC)
val EmeraldInfoLight = Color(0xFF1B6CA0)
val EmeraldInfoContainerLight = Color(0xFFE3F1FA)

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
    background = EmeraldBackgroundDark,
    surface = EmeraldSurfaceDark,
    surfaceElevated = EmeraldSurfaceElevatedDark,
    surfaceHighest = EmeraldSurfaceHighestDark,
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryMuted = EmeraldPrimaryMutedDark,
    textPrimary = EmeraldTextPrimaryDark,
    textSecondary = EmeraldTextSecondaryDark,
    outline = EmeraldOutlineDark,
    outlineSubtle = EmeraldOutlineSubtleDark,
    danger = EmeraldDangerDark,
    onDanger = EmeraldOnDangerDark,
    dangerContainer = EmeraldDangerContainerDark,
    warning = EmeraldWarningDark,
    warningContainer = EmeraldWarningContainerDark,
    info = EmeraldInfoDark,
    infoContainer = EmeraldInfoContainerDark,
    isDark = true,
)

val LightVaultSemanticColors = VaultSemanticColors(
    background = EmeraldBackgroundLight,
    surface = EmeraldSurfaceLight,
    surfaceElevated = EmeraldSurfaceElevatedLight,
    surfaceHighest = EmeraldSurfaceHighestLight,
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryMuted = EmeraldPrimaryMutedLight,
    textPrimary = EmeraldTextPrimaryLight,
    textSecondary = EmeraldTextSecondaryLight,
    outline = EmeraldOutlineLight,
    outlineSubtle = EmeraldOutlineSubtleLight,
    danger = EmeraldDangerLight,
    onDanger = EmeraldOnDangerLight,
    dangerContainer = EmeraldDangerContainerLight,
    warning = EmeraldWarningLight,
    warningContainer = EmeraldWarningContainerLight,
    info = EmeraldInfoLight,
    infoContainer = EmeraldInfoContainerLight,
    isDark = false,
)

val LocalVaultColors = staticCompositionLocalOf { DarkVaultSemanticColors }
