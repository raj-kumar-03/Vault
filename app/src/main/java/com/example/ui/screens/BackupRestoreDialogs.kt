package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.crypto.VaultBackupCodec
import com.example.ui.components.AranIcons
import com.example.ui.components.CategoryChip
import com.example.ui.components.InlineError
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SecretField
import com.example.ui.theme.VaultMonoBodyStyle
import com.example.ui.theme.VaultTheme

private enum class BackupRestoreMethod(val label: String) {
    MASTER_PASSWORD("Master Password"),
    RECOVERY_KEY("Recovery Key"),
}

/**
 * Non-destructive encrypted backup verification & restore confirmation sheet.
 * Displays SHA-256 verified backup metadata and requires unlocking the backup's VEK
 * before committing any change to the local SQLite database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupSheet(
    summary: VaultBackupCodec.BackupInspectionSummary,
    willReplaceExistingVault: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRestoreWithPassword: (masterPassword: String, onResult: (Boolean) -> Unit) -> Unit,
    onRestoreWithRecoveryKey: (recoveryKey: String, newPassword: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    val colors = VaultTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var method by rememberSaveable { mutableStateOf(BackupRestoreMethod.MASTER_PASSWORD) }
    var masterPassword by rememberSaveable { mutableStateOf("") }
    var recoveryKey by rememberSaveable { mutableStateOf("") }
    var newMasterPassword by rememberSaveable { mutableStateOf("") }
    var revealSecret by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryMuted)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Backup v${summary.backupVersion} • SHA-256 Verified",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }

            Text(
                text = "Restore Encrypted Backup",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(
                        imageVector = AranIcons.ShieldFilled,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Vault ${summary.vaultIdPreview} • ${summary.recordCount} encrypted records",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Format v${summary.vaultFormatVersion} • ${summary.kdfSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            if (willReplaceExistingVault) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.warningContainer,
                    border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restoring replaces the current local SQLite vault only after every record in this backup passes AES-256-GCM verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.warning,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BackupRestoreMethod.entries.forEach { option ->
                    CategoryChip(
                        label = option.label,
                        count = null,
                        selected = method == option,
                        onClick = {
                            method = option
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (method == BackupRestoreMethod.MASTER_PASSWORD) {
                SecretField(
                    value = masterPassword,
                    onValueChange = {
                        masterPassword = it
                        errorMessage = null
                    },
                    label = "Backup master password",
                    revealed = revealSecret,
                    onToggleReveal = { revealSecret = !revealSecret },
                    leadingIcon = Icons.Outlined.Lock,
                    testTag = "restore_backup_password_input",
                )
            } else {
                OutlinedTextField(
                    value = recoveryKey,
                    onValueChange = {
                        recoveryKey = it
                        errorMessage = null
                    },
                    label = { Text("Backup Recovery Key") },
                    placeholder = { Text("SVRK-XXXXX-...") },
                    leadingIcon = {
                        Icon(AranIcons.Key, contentDescription = null, tint = colors.primary)
                    },
                    textStyle = VaultMonoBodyStyle.copy(color = colors.textPrimary),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline,
                        cursorColor = colors.primary,
                    ),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_backup_recovery_input"),
                )

                SecretField(
                    value = newMasterPassword,
                    onValueChange = {
                        newMasterPassword = it
                        errorMessage = null
                    },
                    label = "New master password for restored vault",
                    revealed = revealSecret,
                    onToggleReveal = { revealSecret = !revealSecret },
                    leadingIcon = Icons.Outlined.Lock,
                    testTag = "restore_backup_new_pw_input",
                )
            }

            if (errorMessage != null) {
                InlineError(message = errorMessage!!, testTag = "restore_backup_error")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = if (isBusy) "Verifying…" else "Verify & Restore",
                    enabled = !isBusy,
                    onClick = {
                        if (method == BackupRestoreMethod.MASTER_PASSWORD) {
                            if (masterPassword.isEmpty()) {
                                errorMessage = "Enter the backup's master password."
                            } else {
                                onRestoreWithPassword(masterPassword) { ok ->
                                    if (!ok) errorMessage = "Unable to unlock or authenticate backup records."
                                }
                            }
                        } else {
                            when {
                                recoveryKey.isBlank() -> errorMessage = "Enter the backup's recovery key."
                                newMasterPassword.length < 10 -> errorMessage = "Use at least 10 characters for the new master password."
                                else -> onRestoreWithRecoveryKey(recoveryKey, newMasterPassword) { ok ->
                                    if (!ok) errorMessage = "Unable to verify recovery key or backup records."
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    testTag = "restore_backup_submit_button",
                )
            }
        }
    }
}
