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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.GeneratorConfig
import com.example.domain.model.GeneratorMode
import com.example.domain.model.PasswordEngine
import com.example.ui.components.AranIcons
import com.example.ui.components.InlineError
import com.example.ui.components.PasswordStrengthIndicator
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SecretField
import com.example.ui.theme.VaultTheme

@Composable
fun CreateVaultScreen(
    isBusy: Boolean = false,
    onBack: () -> Unit,
    onCreateVaultConfirmed: (masterPassword: String) -> Unit,
) {
    val colors = VaultTheme.colors

    // Volatile remember ensures master passwords never serialize into Android savedInstanceState Bundles.
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var revealMaster by remember { mutableStateOf(false) }
    var revealConfirm by remember { mutableStateOf(false) }
    var acknowledgedLossRisk by rememberSaveable { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showRecoverySheet by remember { mutableStateOf(false) }

    val strength = remember(masterPassword) {
        PasswordEngine.evaluateMasterPassword(masterPassword)
    }

    fun validateAndCreate() {
        when {
            masterPassword.isEmpty() -> {
                validationError = "Enter a master password or passphrase to continue."
            }
            masterPassword.length < 10 -> {
                validationError = "Use at least 10 characters (a 4-word passphrase is easier to remember and stronger)."
            }
            masterPassword != confirmPassword -> {
                validationError = "Passwords do not match. Both fields are preserved so you can review them."
            }
            !acknowledgedLossRisk -> {
                validationError = "Confirm that you understand forgotten master credentials cannot be reset."
            }
            else -> {
                validationError = null
                onCreateVaultConfirmed(masterPassword)
            }
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("create_vault_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back),
                        tint = colors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create Local Vault",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }

            Text(
                text = "Choose a long, unique passphrase. Avoid arbitrary symbol tricks—four or five unrelated words are easier to type accurately and resist guessing.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    SecretField(
                        value = masterPassword,
                        onValueChange = {
                            masterPassword = it
                            if (validationError != null) validationError = null
                        },
                        label = "Master password or passphrase",
                        placeholder = "e.g. cedar-lantern-fjord-42",
                        revealed = revealMaster,
                        onToggleReveal = { revealMaster = !revealMaster },
                        leadingIcon = Icons.Outlined.Lock,
                        imeAction = ImeAction.Next,
                        isError = validationError != null && masterPassword.length < 10,
                        testTag = "create_master_password_input",
                        toggleTestTag = "create_master_password_toggle",
                    )

                    PasswordStrengthIndicator(strength = strength)

                    SecondaryButton(
                        text = "Suggest 4-Word Passphrase",
                        leadingIcon = AranIcons.Sparkle,
                        onClick = {
                            val suggestion = PasswordEngine.generate(
                                GeneratorConfig(
                                    mode = GeneratorMode.PASSPHRASE,
                                    wordCount = 4,
                                    wordSeparator = "-",
                                    capitalizeWords = false,
                                    includePassphraseNumber = true,
                                )
                            )
                            masterPassword = suggestion
                            confirmPassword = suggestion
                            revealMaster = true
                            validationError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "suggest_passphrase_button",
                    )

                    SecretField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            if (validationError != null) validationError = null
                        },
                        label = "Confirm master password",
                        revealed = revealConfirm,
                        onToggleReveal = { revealConfirm = !revealConfirm },
                        leadingIcon = Icons.Outlined.Lock,
                        imeAction = ImeAction.Done,
                        onImeAction = { validateAndCreate() },
                        isError = validationError != null && confirmPassword != masterPassword,
                        testTag = "create_confirm_password_input",
                        toggleTestTag = "create_confirm_password_toggle",
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline Recovery Notice",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.warning,
                        )
                    }

                    Text(
                        text = "Creating this vault generates a random 256-bit Vault Encryption Key (VEK) wrapped by Argon2id and an independent 256-bit Recovery Key. Because Aran has no cloud account, losing both makes the vault permanently unrecoverable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )

                    TextButton(
                        onClick = { showRecoverySheet = true },
                        modifier = Modifier.testTag("create_vault_recovery_info_button"),
                    ) {
                        Text(
                            text = "How local recovery works",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                acknowledgedLossRisk = !acknowledgedLossRisk
                                if (validationError != null) validationError = null
                            }
                            .padding(vertical = 4.dp)
                            .testTag("create_vault_acknowledge_checkbox"),
                    ) {
                        Checkbox(
                            checked = acknowledgedLossRisk,
                            onCheckedChange = {
                                acknowledgedLossRisk = it
                                if (validationError != null) validationError = null
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.primary,
                                checkmarkColor = colors.onPrimary,
                                uncheckedColor = colors.outline,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I understand that forgetting both my master password and recovery key makes my encrypted local SQLite vault permanently unrecoverable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                        )
                    }
                }
            }

            if (validationError != null) {
                InlineError(
                    message = validationError!!,
                    testTag = "create_vault_error",
                )
            }

            PrimaryButton(
                text = if (isBusy) "Deriving Key & Creating Vault…" else "Create Encrypted Vault",
                enabled = !isBusy,
                onClick = { validateAndCreate() },
                modifier = Modifier.fillMaxWidth(),
                testTag = "create_vault_submit_button",
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRecoverySheet) {
        RecoveryInfoSheet(
            mode = EducationSheetMode.RECOVERY,
            onDismiss = { showRecoverySheet = false },
        )
    }
}
