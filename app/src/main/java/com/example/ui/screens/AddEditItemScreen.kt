package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.domain.model.PasswordEngine
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import com.example.ui.components.CategoryChip
import com.example.ui.components.InlineError
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SecretField
import com.example.ui.theme.VaultTheme

@Composable
fun AddEditItemScreen(
    existingItem: VaultItem?,
    initialGeneratedPassword: String?,
    onCancel: () -> Unit,
    onSave: (VaultItem) -> Unit,
) {
    val colors = VaultTheme.colors
    val isEditing = existingItem != null

    var itemType by rememberSaveable(existingItem?.id) {
        mutableStateOf(existingItem?.type ?: VaultItemType.LOGIN)
    }
    var title by rememberSaveable(existingItem?.id) {
        mutableStateOf(existingItem?.title ?: "")
    }
    var username by rememberSaveable(existingItem?.id) {
        mutableStateOf(existingItem?.username ?: "")
    }
    var secret by rememberSaveable(existingItem?.id, initialGeneratedPassword) {
        mutableStateOf(existingItem?.secret ?: initialGeneratedPassword.orEmpty())
    }
    var revealSecret by rememberSaveable { mutableStateOf(false) }
    var website by rememberSaveable(existingItem?.id) {
        mutableStateOf(existingItem?.website ?: "")
    }
    var notes by rememberSaveable(existingItem?.id) {
        mutableStateOf(existingItem?.notes ?: "")
    }
    var isFavorite by rememberSaveable(existingItem?.id) {
        mutableStateOf(existingItem?.isFavorite ?: false)
    }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val domainPreview = remember(website) { PasswordEngine.normalizeDomain(website) }

    fun validateAndSave() {
        val trimmedTitle = title.trim()
        when {
            trimmedTitle.isEmpty() -> {
                validationError = "Title is required so you can find this item later."
            }
            itemType == VaultItemType.LOGIN && secret.isEmpty() -> {
                validationError = "Enter or generate a password for this login."
            }
            website.isNotBlank() && !domainPreview.isValid -> {
                validationError = "Website address looks invalid. Example: https://example.com"
            }
            else -> {
                validationError = null
                // IMPORTANT: Do NOT trim or alter the secret/password string!
                val itemToSave = VaultItem(
                    id = existingItem?.id ?: "",
                    type = itemType,
                    title = trimmedTitle,
                    username = username.trim(),
                    secret = secret,
                    website = website.trim(),
                    normalizedDomain = domainPreview.normalizedDomain,
                    notes = notes.trim(),
                    isFavorite = isFavorite,
                    createdAtLabel = existingItem?.createdAtLabel ?: "Just now",
                    updatedAtLabel = "Just now",
                    lastUsedEpochMs = existingItem?.lastUsedEpochMs ?: 0L,
                    secondaryIdentifier = when (itemType) {
                        VaultItemType.PAYMENT_CARD -> {
                            val digits = secret.filter { it.isDigit() }
                            if (digits.length >= 4) "•••• ${digits.takeLast(4)}" else "Card • Masked"
                        }
                        VaultItemType.SECURE_NOTE -> "Encrypted note • Masked"
                        else -> ""
                    },
                )
                onSave(itemToSave)
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceElevated,
        unfocusedContainerColor = colors.surface,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        errorBorderColor = colors.danger,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.primary,
    )

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("editor_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back),
                        tint = colors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEditing) "Edit Item" else "Add Credential",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(VaultItemType.entries) { type ->
                    CategoryChip(
                        label = type.displayLabel,
                        count = null,
                        selected = itemType == type,
                        onClick = { itemType = type },
                        testTag = "editor_type_${type.name.lowercase()}",
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (validationError != null) validationError = null
                        },
                        label = { Text("Title") },
                        placeholder = { Text("e.g. CodeForge Git") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Title, contentDescription = null, tint = colors.textSecondary)
                        },
                        singleLine = true,
                        isError = validationError != null && title.isBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input"),
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = {
                            Text(
                                when (itemType) {
                                    VaultItemType.LOGIN -> "Username or Email"
                                    VaultItemType.PASSKEY -> "Passkey Account / User Handle"
                                    VaultItemType.PAYMENT_CARD -> "Cardholder Name"
                                    VaultItemType.IDENTITY -> "Full Name / Email"
                                    VaultItemType.SECURE_NOTE -> "Category / Label"
                                }
                            )
                        },
                        placeholder = { Text("alex.chen@example.com") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.textSecondary)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_username_input"),
                    )

                    SecretField(
                        value = secret,
                        onValueChange = {
                            // Preserve exact characters, including leading/trailing whitespace if entered.
                            secret = it
                            if (validationError != null) validationError = null
                        },
                        label = when (itemType) {
                            VaultItemType.LOGIN -> "Password"
                            VaultItemType.PASSKEY -> "Passkey Reference / Metadata"
                            VaultItemType.SECURE_NOTE -> "Protected Note Secret"
                            VaultItemType.IDENTITY -> "Protected ID / Address"
                            VaultItemType.PAYMENT_CARD -> "Card Number / Details"
                        },
                        revealed = revealSecret,
                        onToggleReveal = { revealSecret = !revealSecret },
                        leadingIcon = Icons.Outlined.Lock,
                        supportingText = "Passwords are never trimmed or altered silently.",
                        isError = validationError != null && itemType == VaultItemType.LOGIN && secret.isEmpty(),
                        imeAction = ImeAction.Next,
                        testTag = "editor_secret_input",
                        toggleTestTag = "editor_secret_toggle",
                    )

                    if (itemType == VaultItemType.LOGIN) {
                        SecondaryButton(
                            text = "Generate Strong Password",
                            leadingIcon = Icons.Outlined.AutoAwesome,
                            onClick = {
                                secret = PasswordEngine.generate(GeneratorConfig(length = 20))
                                revealSecret = true
                                validationError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "editor_generate_password_button",
                        )
                    }

                    OutlinedTextField(
                        value = website,
                        onValueChange = {
                            website = it
                            if (validationError != null) validationError = null
                        },
                        label = { Text("Website (optional)") },
                        placeholder = { Text("https://example.com") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Language, contentDescription = null, tint = colors.textSecondary)
                        },
                        supportingText = {
                            Text(
                                text = domainPreview.guidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!domainPreview.isValid) {
                                    colors.danger
                                } else if (domainPreview.normalizedDomain.isNotEmpty()) {
                                    colors.primary
                                } else {
                                    colors.textSecondary
                                },
                                modifier = Modifier.testTag("editor_domain_preview"),
                            )
                        },
                        isError = website.isNotBlank() && !domainPreview.isValid,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_website_input"),
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        placeholder = { Text("Add context or recovery hints (avoid real secrets in Phase 1)") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_notes_input"),
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(
                                text = "Pin to Favorites",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Keep at the top of your Vault home screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Switch(
                            checked = isFavorite,
                            onCheckedChange = { isFavorite = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surfaceElevated,
                            ),
                            modifier = Modifier.testTag("editor_favorite_switch"),
                        )
                    }
                }
            }

            if (validationError != null) {
                InlineError(
                    message = validationError!!,
                    testTag = "editor_validation_error",
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    testTag = "editor_cancel_button",
                )
                PrimaryButton(
                    text = if (isEditing) "Save Changes" else "Save Item",
                    onClick = { validateAndSave() },
                    modifier = Modifier.weight(1.3f),
                    testTag = "editor_save_button",
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
