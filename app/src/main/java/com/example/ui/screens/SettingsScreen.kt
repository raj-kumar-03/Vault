package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.VaultCryptoMetadata
import com.example.domain.model.AutoLockInterval
import com.example.domain.model.ClipboardClearInterval
import com.example.domain.model.SecuritySettings
import com.example.domain.model.ThemePreference
import com.example.ui.components.CategoryChip
import com.example.ui.components.SettingsSection
import com.example.ui.theme.VaultTheme

@Composable
fun SettingsScreen(
    settings: SecuritySettings,
    cryptoMetadata: VaultCryptoMetadata,
    isCryptoBusy: Boolean,
    onAutoLockChange: (AutoLockInterval) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onClipboardClearChange: (ClipboardClearInterval) -> Unit,
    onHideAppPreviewChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onChangeMasterPassword: (current: String, newPw: String, onResult: (Boolean) -> Unit) -> Unit,
    onRunKdfBenchmark: () -> Unit,
    onLockVaultNow: () -> Unit,
) {
    val colors = VaultTheme.colors
    var showRecoverySheet by remember { mutableStateOf(false) }
    var showChangePasswordSheet by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.testTag("settings_screen_title"),
                    )
                    Text(
                        text = "Transparent controls distinguishing active prototype options from planned phases",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            item(key = "security_section") {
                SettingsSection(
                    title = "Security",
                    subtitle = "Local vault session and clipboard preferences",
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Auto-lock interval",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AutoLockInterval.entries.take(2).forEach { interval ->
                                CategoryChip(
                                    label = interval.label,
                                    count = null,
                                    selected = settings.autoLockInterval == interval,
                                    onClick = { onAutoLockChange(interval) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AutoLockInterval.entries.drop(2).forEach { interval ->
                                CategoryChip(
                                    label = interval.label,
                                    count = null,
                                    selected = settings.autoLockInterval == interval,
                                    onClick = { onAutoLockChange(interval) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Text(
                            text = "Background timer enforcement ships with platform lifecycle integration in Phase 4.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsSwitchRow(
                        title = "Biometric unlock affordance",
                        subtitle = "Shows biometric prototype trigger on Unlock screen (Android Keystore binding in Phase 4)",
                        checked = settings.biometricUnlockEnabled,
                        onCheckedChange = onBiometricChange,
                        testTag = "settings_biometric_switch",
                    )

                    HorizontalDivider(color = colors.outlineSubtle)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Clipboard auto-clear (best-effort)",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ClipboardClearInterval.entries.forEach { interval ->
                                CategoryChip(
                                    label = interval.label,
                                    count = null,
                                    selected = settings.clipboardClearInterval == interval,
                                    onClick = { onClipboardClearChange(interval) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Text(
                            text = "Android OS and keyboard clipboards may keep history independently; clearing is best-effort only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsSwitchRow(
                        title = "Hide app switcher preview (FLAG_SECURE)",
                        subtitle = "Blocks screenshots and hides recent-apps thumbnails when enabled",
                        checked = settings.hideAppPreviewEnabled,
                        onCheckedChange = onHideAppPreviewChange,
                        testTag = "settings_flag_secure_switch",
                    )

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsActionRow(
                        title = "Change master password",
                        subtitle = "Re-wraps VEK with fresh Argon2id salt without re-encrypting records",
                        badge = null,
                        onClick = { showChangePasswordSheet = true },
                        testTag = "settings_change_password_row",
                    )

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsInfoRow(
                        title = "Cryptographic engine (Format v${cryptoMetadata.formatVersion})",
                        subtitle = "${cryptoMetadata.kdfSummary} • AES-256-GCM + AAD • ${cryptoMetadata.encryptedRecordCount} encrypted records",
                        badge = "Active",
                    )

                    HorizontalDivider(color = colors.outlineSubtle)

                    val benchmarkSubtitle = cryptoMetadata.lastBenchmark?.let { res ->
                        "Last measured on this device: ${res.durationMs} ms (${res.parameters.summaryLabel()})"
                    } ?: "Measure Argon2id (19 MiB, t=2, p=1) derivation latency on this device"

                    SettingsActionRow(
                        title = if (isCryptoBusy) "Running Argon2id benchmark…" else "Benchmark Argon2id on this device",
                        subtitle = benchmarkSubtitle,
                        badge = cryptoMetadata.lastBenchmark?.let { "${it.durationMs} ms" },
                        onClick = { if (!isCryptoBusy) onRunKdfBenchmark() },
                        testTag = "settings_benchmark_kdf_row",
                    )

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsActionRow(
                        title = "Lock vault now",
                        subtitle = "Wipes VEK from memory and returns to Unlock screen",
                        badge = null,
                        onClick = onLockVaultNow,
                        testTag = "settings_lock_now_row",
                    )
                }
            }

            item(key = "autofill_section") {
                SettingsSection(
                    title = "Autofill",
                    subtitle = "Android system autofill integration",
                ) {
                    SettingsInfoRow(
                        title = "Android AutofillService",
                        subtitle = "Strict origin & app-signature matching without cross-origin leakage.",
                        badge = "Coming in Phase 5",
                    )
                }
            }

            item(key = "backup_section") {
                SettingsSection(
                    title = "Backup & Recovery",
                    subtitle = "Offline encrypted export and independent recovery key",
                ) {
                    SettingsInfoRow(
                        title = "Encrypted Vault Export & Restore",
                        subtitle = "Versioned authenticated backup files are disabled until Phase 3 storage & Phase 2 cryptography pass review.",
                        badge = "Coming in Phases 2–3",
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsActionRow(
                        title = "Learn about local recovery",
                        subtitle = "Understand master password + independent recovery key design",
                        badge = null,
                        onClick = { showRecoverySheet = true },
                        testTag = "settings_recovery_guide_row",
                    )
                }
            }

            item(key = "appearance_section") {
                SettingsSection(
                    title = "Appearance",
                    subtitle = "Midnight Emerald dark-first visual system",
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ThemePreference.entries.forEach { pref ->
                                CategoryChip(
                                    label = pref.label,
                                    count = null,
                                    selected = settings.themePreference == pref,
                                    onClick = { onThemeChange(pref) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "settings_theme_${pref.name.lowercase()}",
                                )
                            }
                        }
                    }
                }
            }

            item(key = "about_section") {
                SettingsSection(
                    title = "About",
                ) {
                    SettingsInfoRow(
                        title = "SecureVault Android",
                        subtitle = "Version 0.2.0-phase2 • Argon2id + AES-256-GCM crypto engine (in-memory)",
                        badge = "Phase 2",
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsActionRow(
                        title = "Privacy Statement",
                        subtitle = "Zero accounts, zero telemetry, zero remote servers",
                        badge = null,
                        onClick = { showPrivacyDialog = true },
                        testTag = "settings_privacy_row",
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsActionRow(
                        title = "Open-Source Licenses",
                        subtitle = "Kotlin, Jetpack Compose, Bouncy Castle (MIT), Plus Jakarta Sans, JetBrains Mono",
                        badge = null,
                        onClick = { showLicensesDialog = true },
                        testTag = "settings_licenses_row",
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showChangePasswordSheet) {
        ChangeMasterPasswordSheet(
            isBusy = isCryptoBusy,
            onDismiss = { showChangePasswordSheet = false },
            onSubmit = { current, newPw, onResult ->
                onChangeMasterPassword(current, newPw) { ok ->
                    if (ok) showChangePasswordSheet = false
                    onResult(ok)
                }
            },
        )
    }

    if (showRecoverySheet) {
        RecoveryInfoSheet(
            mode = EducationSheetMode.RECOVERY,
            onDismiss = { showRecoverySheet = false },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Local-First Privacy Statement") },
            text = {
                Text(
                    "SecureVault does not connect to any cloud backend, analytics provider, crash reporter, or third-party API.\n\n" +
                        "Phase 1 Prototype Notice: All data in this build lives strictly in memory as synthetic mock records. Do not enter real passwords until Phase 2 cryptography and Phase 3 encrypted persistence are complete and reviewed."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close", color = colors.primary)
                }
            },
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Open-Source Components") },
            text = {
                Text(
                    "• Kotlin & Kotlinx Coroutines (Apache 2.0)\n" +
                        "• AndroidX Jetpack Compose, Navigation & Material 3 (Apache 2.0)\n" +
                        "• Plus Jakarta Sans Font (SIL Open Font License 1.1)\n" +
                        "• JetBrains Mono Font (SIL Open Font License 1.1)"
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close", color = colors.primary)
                }
            },
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val colors = VaultTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceElevated,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    badge: String,
) {
    val colors = VaultTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = VaultTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}
