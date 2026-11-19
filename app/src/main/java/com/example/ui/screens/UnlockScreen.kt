package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PersistentVaultRepository
import com.example.ui.components.InlineError
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecretField
import com.example.ui.components.ShieldEmblem
import com.example.ui.theme.VaultMonoLabelStyle
import com.example.ui.theme.VaultTheme

@Composable
fun UnlockScreen(
    biometricEnabled: Boolean,
    isBusy: Boolean,
    onUnlockWithPassword: (candidate: String, onResult: (Boolean) -> Unit) -> Unit,
    onUnlockWithBiometric: (onResult: (Boolean) -> Unit) -> Unit,
    onRecoverWithKey: (recoveryKey: String, newMasterPassword: String, onResult: (Boolean) -> Unit) -> Unit,
    onReturnToWelcome: () -> Unit,
) {
    val colors = VaultTheme.colors
    var password by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showRecoverSheet by remember { mutableStateOf(false) }

    val genericError = stringResource(R.string.error_unlock_generic)
    val bioDesc = stringResource(R.string.cd_biometric_unlock)

    fun submitUnlock() {
        if (isBusy) return
        if (password.isEmpty()) {
            errorMessage = genericError
            return
        }
        onUnlockWithPassword(password) { ok ->
            if (!ok) {
                errorMessage = genericError
            } else {
                errorMessage = null
                password = ""
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.surfaceElevated.copy(alpha = if (colors.isDark) 0.45f else 0.2f),
                        colors.background,
                        colors.background,
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Vault Locked • VEK Wiped From Memory",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ShieldEmblem(size = 76.dp, locked = true)

            Text(
                text = "Unlock Your Vault",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (biometricEnabled) {
                    "Enter your master password to derive your Argon2id key, or use the biometric prototype."
                } else {
                    "Enter your master password to unwrap your Vault Encryption Key."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            if (biometricEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isBusy) {
                                onUnlockWithBiometric { ok ->
                                    if (!ok) errorMessage = genericError
                                }
                            }
                            .semantics { contentDescription = bioDesc }
                            .testTag("unlock_biometric_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                    Text(
                        text = "Biometric prototype (hardware Keystore binding in Phase 4)",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = colors.outlineSubtle)
                    Text(
                        text = "or",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = colors.outlineSubtle)
                }
            }

            if (errorMessage != null) {
                InlineError(
                    message = errorMessage!!,
                    testTag = "unlock_error_banner",
                )
            }

            SecretField(
                value = password,
                onValueChange = {
                    password = it
                    if (errorMessage != null) errorMessage = null
                },
                label = "Master Password",
                revealed = revealed,
                onToggleReveal = { revealed = !revealed },
                leadingIcon = Icons.Outlined.Lock,
                imeAction = ImeAction.Done,
                onImeAction = { submitUnlock() },
                isError = errorMessage != null,
                testTag = "unlock_password_input",
                toggleTestTag = "unlock_password_toggle",
            )

            PrimaryButton(
                text = if (isBusy) "Deriving Key…" else "Unlock",
                enabled = !isBusy,
                onClick = { submitUnlock() },
                modifier = Modifier.fillMaxWidth(),
                testTag = "unlock_submit_button",
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(12.dp),
                ) {
                    Text(
                        text = "Phase 3 local SQLite hint",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "Demo vault passphrase: ${PersistentVaultRepository.DEFAULT_DEMO_PASSPHRASE} (or your master password).",
                        style = VaultMonoLabelStyle,
                        color = colors.primary,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { showRecoverSheet = true },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("unlock_use_recovery_key_button"),
                ) {
                    Text(
                        text = "Use Recovery Key",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.outline,
                )
                TextButton(
                    onClick = onReturnToWelcome,
                    enabled = !isBusy,
                    modifier = Modifier.testTag("unlock_welcome_button"),
                ) {
                    Text(
                        text = "Onboarding",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }

    if (showRecoverSheet) {
        RecoverVaultSheet(
            isBusy = isBusy,
            onDismiss = { showRecoverSheet = false },
            onSubmitRecovery = { recoveryKey, newPw, onResult ->
                onRecoverWithKey(recoveryKey, newPw) { ok ->
                    if (ok) showRecoverSheet = false
                    onResult(ok)
                }
            },
        )
    }
}
