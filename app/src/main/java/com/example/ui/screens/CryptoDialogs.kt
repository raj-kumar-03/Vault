package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.PasswordEngine
import com.example.ui.components.AranIcons
import com.example.ui.components.CopyAction
import com.example.ui.components.InlineError
import com.example.ui.components.PasswordStrengthIndicator
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SecretField
import com.example.ui.theme.VaultMonoBodyStyle
import com.example.ui.theme.VaultTheme
import kotlinx.coroutines.delay

/**
 * Displays the newly generated independent 256-bit Recovery Key once after vault creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryKeyCreatedSheet(
    recoveryKey: String,
    onCopyRecoveryKey: (String) -> Unit,
    onConfirmedSaved: () -> Unit,
) {
    val colors = VaultTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000L)
            copied = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onConfirmedSaved,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    text = "256-bit Independent Recovery Key",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }

            Text(
                text = "Save Your Recovery Key",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Text(
                text = "This key independently wraps your Vault Encryption Key (VEK) via HKDF-SHA256. Store it offline—if you forget your master password, it can reset access without re-encrypting your vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = recoveryKey,
                    style = VaultMonoBodyStyle,
                    color = colors.primary,
                    modifier = Modifier.testTag("recovery_key_display_text"),
                )
            }

            CopyAction(
                label = "Copy Recovery Key",
                copied = copied,
                onClick = {
                    copied = true
                    onCopyRecoveryKey(recoveryKey)
                },
                testTag = "recovery_key_copy_button",
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.warningContainer,
                border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.4f)),
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
                        text = "Shown once upon vault creation. Store this key offline so you can still unlock your encrypted SQLite vault if you forget your master password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warning,
                    )
                }
            }

            PrimaryButton(
                text = "I Have Recorded My Recovery Key",
                onClick = onConfirmedSaved,
                modifier = Modifier.fillMaxWidth(),
                testTag = "recovery_key_confirm_saved_button",
            )
        }
    }
}

/**
 * Re-wraps the existing VEK under a fresh salt and new Argon2id KEK without re-encrypting records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeMasterPasswordSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    val colors = VaultTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var revealCurrent by remember { mutableStateOf(false) }
    var revealNew by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val strength = remember(newPassword) { PasswordEngine.evaluateMasterPassword(newPassword) }

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
            Text(
                text = "Change Master Password",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Text(
                text = "Derives a fresh Argon2id KEK with a new salt and re-wraps only your Vault Encryption Key (VEK). Existing encrypted records do not require re-encryption.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            SecretField(
                value = currentPassword,
                onValueChange = {
                    currentPassword = it
                    errorMessage = null
                },
                label = "Current master password",
                revealed = revealCurrent,
                onToggleReveal = { revealCurrent = !revealCurrent },
                leadingIcon = Icons.Outlined.Lock,
                testTag = "change_pw_current_input",
            )

            SecretField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    errorMessage = null
                },
                label = "New master password",
                revealed = revealNew,
                onToggleReveal = { revealNew = !revealNew },
                leadingIcon = Icons.Outlined.Lock,
                testTag = "change_pw_new_input",
            )

            PasswordStrengthIndicator(strength = strength)

            SecretField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = null
                },
                label = "Confirm new master password",
                revealed = revealNew,
                onToggleReveal = { revealNew = !revealNew },
                leadingIcon = Icons.Outlined.Lock,
                testTag = "change_pw_confirm_input",
            )

            if (errorMessage != null) {
                InlineError(message = errorMessage!!, testTag = "change_pw_error")
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
                    text = if (isBusy) "Re-wrapping…" else "Update Password",
                    enabled = !isBusy,
                    onClick = {
                        when {
                            currentPassword.isEmpty() -> errorMessage = "Enter your current master password."
                            newPassword.length < 10 -> errorMessage = "Use at least 10 characters for your new master password."
                            newPassword != confirmPassword -> errorMessage = "New passwords do not match."
                            else -> {
                                onSubmit(currentPassword, newPassword) { success ->
                                    if (!success) {
                                        errorMessage = "Unable to verify current master password."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    testTag = "change_pw_submit_button",
                )
            }
        }
    }
}

/**
 * Unwraps the VEK using the independent Recovery Key and resets the master password wrapper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoverVaultSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmitRecovery: (recoveryKey: String, newMasterPassword: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    val colors = VaultTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var recoveryKeyInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var revealNew by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            Text(
                text = "Recover Vault with Recovery Key",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Text(
                text = "Enter your SVRK-… recovery key to unwrap your Vault Encryption Key and set a new master password without re-encrypting your vault records.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            OutlinedTextField(
                value = recoveryKeyInput,
                onValueChange = {
                    recoveryKeyInput = it
                    errorMessage = null
                },
                label = { Text("Recovery Key") },
                placeholder = { Text("SVRK-XXXXX-XXXXX-...") },
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
                    .testTag("recover_key_input"),
            )

            SecretField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    errorMessage = null
                },
                label = "New master password",
                revealed = revealNew,
                onToggleReveal = { revealNew = !revealNew },
                leadingIcon = Icons.Outlined.Lock,
                testTag = "recover_new_pw_input",
            )

            SecretField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = null
                },
                label = "Confirm new master password",
                revealed = revealNew,
                onToggleReveal = { revealNew = !revealNew },
                leadingIcon = Icons.Outlined.Lock,
                testTag = "recover_confirm_pw_input",
            )

            if (errorMessage != null) {
                InlineError(message = errorMessage!!, testTag = "recover_vault_error")
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
                    text = if (isBusy) "Recovering…" else "Recover & Unlock",
                    enabled = !isBusy,
                    onClick = {
                        when {
                            recoveryKeyInput.isBlank() -> errorMessage = "Enter your recovery key."
                            newPassword.length < 10 -> errorMessage = "Use at least 10 characters for your new master password."
                            newPassword != confirmPassword -> errorMessage = "New passwords do not match."
                            else -> {
                                onSubmitRecovery(recoveryKeyInput, newPassword) { success ->
                                    if (!success) {
                                        errorMessage = "Unable to verify recovery key. Check all characters and try again."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    testTag = "recover_submit_button",
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
