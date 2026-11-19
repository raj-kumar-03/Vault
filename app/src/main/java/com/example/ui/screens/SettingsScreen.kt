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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.VaultCryptoMetadata
import com.example.domain.model.AutoLockInterval
import com.example.domain.model.ClipboardClearInterval
import com.example.domain.model.SecuritySettings
import com.example.domain.model.ThemePreference
import com.example.security.BiometricAvailability
import com.example.security.BiometricKeyState
import com.example.ui.components.CategoryChip
import com.example.ui.components.SettingsSection
import com.example.ui.theme.VaultTheme

@Composable
fun SettingsScreen(
    settings: SecuritySettings,
    biometricState: BiometricKeyState,
    cryptoMetadata: VaultCryptoMetadata,
    isCryptoBusy: Boolean,
    isAutofillServiceEnabled: Boolean = false,
    isPasskeyProviderSupported: Boolean = true,
    onAutoLockChange: (AutoLockInterval) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onAutoPromptBiometricChange: (Boolean) -> Unit = {},
    onClipboardClearChange: (ClipboardClearInterval) -> Unit,
    onHideAppPreviewChange: (Boolean) -> Unit,
    onAllowInsecureHttpAutofillChange: (Boolean) -> Unit = {},
    onOpenAutofillSystemSettings: () -> Unit = {},
    onOpenPasskeySystemSettings: () -> Unit = {},
    onThemeChange: (ThemePreference) -> Unit,
    onChangeMasterPassword: (current: String, newPw: String, onResult: (Boolean) -> Unit) -> Unit,
    onRunKdfBenchmark: () -> Unit,
    onExportEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    onLockVaultNow: () -> Unit,
) {
    val colors = VaultTheme.colors
    var showRecoverySheet by remember { mutableStateOf(false) }
    var showChangePasswordSheet by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val (bioBadgeText, bioBadgeColor) = when {
        biometricState.canUnlockWithBiometrics -> "Bound" to colors.primary
        biometricState.availability == BiometricAvailability.READY -> "Ready" to colors.info
        biometricState.availability == BiometricAvailability.KEY_INVALIDATED -> "Invalidated" to colors.warning
        biometricState.availability == BiometricAvailability.NO_BIOMETRICS_ENROLLED -> "Not Enrolled" to colors.warning
        biometricState.availability == BiometricAvailability.DEVICE_LOCK_NOT_SET -> "No Lock" to colors.danger
        else -> "Unavailable" to colors.textSecondary
    }

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
                        text = "Android Keystore protection, monotonic auto-lock, and local-first controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            item(key = "security_section") {
                SettingsSection(
                    title = "Platform Security & Authentication",
                    subtitle = "Hardware-bound Keystore unlock, auto-lock lifecycle, and screen privacy",
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
                            text = "Active: enforced via monotonic clock across backgrounding, foreground inactivity, and process death.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsSwitchRow(
                        title = "Android Keystore biometric unlock",
                        subtitle = biometricState.statusMessage,
                        checked = biometricState.canUnlockWithBiometrics,
                        enabled = !isCryptoBusy && (biometricState.canEnrollBiometrics || biometricState.hasBoundEnvelope),
                        badge = bioBadgeText,
                        badgeColor = bioBadgeColor,
                        onCheckedChange = onBiometricChange,
                        testTag = "settings_biometric_switch",
                    )

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsSwitchRow(
                        title = "Prompt fingerprint or face on app entry",
                        subtitle = "Automatically invokes BiometricPrompt immediately when launching or resuming a locked vault",
                        checked = settings.autoPromptBiometricOnLaunch,
                        enabled = biometricState.canUnlockWithBiometrics,
                        badge = if (biometricState.canUnlockWithBiometrics && settings.autoPromptBiometricOnLaunch) "Auto" else "Manual",
                        badgeColor = if (biometricState.canUnlockWithBiometrics && settings.autoPromptBiometricOnLaunch) colors.primary else colors.textSecondary,
                        onCheckedChange = onAutoPromptBiometricChange,
                        testTag = "settings_biometric_autoprompt_switch",
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
                            text = "Marks copied secrets as sensitive (Android 13+) and clears only matching secrets on timeout or lock. OS/keyboard clipboard histories remain outside app control.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }

                    HorizontalDivider(color = colors.outlineSubtle)

                    SettingsSwitchRow(
                        title = "Hide app switcher preview (FLAG_SECURE)",
                        subtitle = "Active: blocks screenshots, screen recordings, and recent-apps thumbnails",
                        checked = settings.hideAppPreviewEnabled,
                        enabled = true,
                        badge = if (settings.hideAppPreviewEnabled) "Protected" else "Off",
                        badgeColor = if (settings.hideAppPreviewEnabled) colors.primary else colors.warning,
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
                        badgeColor = colors.primary,
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
                        subtitle = "Wipes VEK from memory, clears matching secret clipboard, and returns to Unlock",
                        badge = null,
                        onClick = onLockVaultNow,
                        testTag = "settings_lock_now_row",
                    )
                }
            }

            item(key = "autofill_section") {
                SettingsSection(
                    title = "Autofill & Offline Passkeys",
                    subtitle = "Strict origin matching, APK signature verification, and Android 14+ FIDO2 ES256 passkeys",
                ) {
                    SettingsActionRow(
                        title = "Android AutofillService",
                        subtitle = if (isAutofillServiceEnabled) {
                            "Active as system autofill provider • Strict HTTPS host & APK SHA-256 verification"
                        } else {
                            "Tap to select Aran as your Android autofill service in System Settings"
                        },
                        badge = if (isAutofillServiceEnabled) "Active" else "Enable",
                        onClick = onOpenAutofillSystemSettings,
                        testTag = "settings_autofill_row",
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsSwitchRow(
                        title = "Block insecure HTTP autofill",
                        subtitle = "Refuses autofill on plaintext http:// websites by default to prevent credential interception",
                        checked = !settings.allowInsecureHttpAutofill,
                        enabled = true,
                        badge = if (!settings.allowInsecureHttpAutofill) "Protected" else "Exception",
                        badgeColor = if (!settings.allowInsecureHttpAutofill) colors.primary else colors.warning,
                        onCheckedChange = { blockHttp -> onAllowInsecureHttpAutofillChange(!blockHttp) },
                        testTag = "settings_block_http_autofill_switch",
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsActionRow(
                        title = "Offline Android Passkey Provider (API 34+)",
                        subtitle = if (isPasskeyProviderSupported) {
                            "Registered CredentialProviderService • ECDSA P-256 (ES256) keys encrypted inside local AES-256-GCM vault"
                        } else {
                            "Requires Android 14 (API 34) or higher for system Credential Manager passkeys"
                        },
                        badge = if (isPasskeyProviderSupported) "Ready" else "API 34+",
                        onClick = onOpenPasskeySystemSettings,
                        testTag = "settings_passkey_provider_row",
                    )
                }
            }

            item(key = "backup_section") {
                SettingsSection(
                    title = "Backup & Recovery",
                    subtitle = "Versioned .svbk v1 encrypted export & SHA-256 verified non-destructive restore",
                ) {
                    SettingsActionRow(
                        title = "Export encrypted backup (.svbk)",
                        subtitle = "Saves your AES-256-GCM encrypted vault and wrapped VEKs offline via Android file picker",
                        badge = "v1",
                        onClick = { if (!isCryptoBusy) onExportEncryptedBackup() },
                        testTag = "settings_export_backup_row",
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsActionRow(
                        title = "Restore from encrypted backup",
                        subtitle = "Verifies SHA-256 integrity and authenticates every record before touching local SQLite",
                        badge = null,
                        onClick = { if (!isCryptoBusy) onRestoreEncryptedBackup() },
                        testTag = "settings_restore_backup_row",
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
                    subtitle = "Aran dark charcoal & lime visual system",
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
                        title = "Aran Android",
                        subtitle = "Version 0.5.0-phase5 • Native SQLite + Argon2id + Keystore Biometrics + Autofill + Offline Passkeys",
                        badge = "Phase 5",
                        badgeColor = colors.primary,
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsActionRow(
                        title = "Privacy Statement",
                        subtitle = "Zero accounts, zero telemetry, zero cloud backup, zero remote servers",
                        badge = null,
                        onClick = { showPrivacyDialog = true },
                        testTag = "settings_privacy_row",
                    )
                    HorizontalDivider(color = colors.outlineSubtle)
                    SettingsActionRow(
                        title = "Open-Source Licenses",
                        subtitle = "Kotlin, Jetpack Compose, AndroidX Biometric, Bouncy Castle (MIT), Plus Jakarta Sans, JetBrains Mono",
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
                    "Aran does not connect to any cloud backend, analytics provider, crash reporter, or third-party API.\n\n" +
                        "Phase 5 Security Hardening: Vault records and FIDO2/WebAuthn ES256 passkey private keys persist strictly on-device in AES-256-GCM encrypted SQLite (with OS Auto Backup disabled). Entry-point BiometricPrompt wraps your VEK inside Android Keystore, and Autofill strictly enforces HTTPS origin and APK SHA-256 verification."
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
                        "• AndroidX Jetpack Compose, Navigation, Biometric, Credentials & Material 3 (Apache 2.0)\n" +
                        "• Native Android SQLiteOpenHelper, AutofillService & Android Keystore Platform APIs\n" +
                        "• Bouncy Castle Crypto Provider — Argon2id & HKDF (MIT License)\n" +
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
    enabled: Boolean,
    badge: String?,
    badgeColor: Color,
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
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
    badgeColor: Color,
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
                color = badgeColor,
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
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}
