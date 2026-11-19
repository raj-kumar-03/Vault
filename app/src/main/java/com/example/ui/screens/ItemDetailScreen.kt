package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.CopyAction
import com.example.ui.components.DestructiveButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.itemTypeIcon
import com.example.ui.theme.VaultMonoBodyStyle
import com.example.ui.theme.VaultTheme
import kotlinx.coroutines.delay

@Composable
fun ItemDetailScreen(
    item: VaultItem?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onCopyField: (label: String, value: String, isSecret: Boolean) -> Unit,
) {
    val colors = VaultTheme.colors
    if (item == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Item no longer exists",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                SecondaryButton(text = "Back to Vault", onClick = onBack)
            }
        }
        return
    }

    ItemDetailContent(
        item = item,
        showBackButton = true,
        onBack = onBack,
        onToggleFavorite = onToggleFavorite,
        onEdit = onEdit,
        onDeleteConfirmed = onDeleteConfirmed,
        onCopyField = onCopyField,
    )
}

@Composable
fun ItemDetailContent(
    item: VaultItem,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onCopyField: (label: String, value: String, isSecret: Boolean) -> Unit,
) {
    val colors = VaultTheme.colors
    var secretRevealed by remember(item.id) { mutableStateOf(false) }
    var secondsRemaining by remember(item.id) { mutableIntStateOf(15) }
    var copiedKey by remember(item.id) { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember(item.id) { mutableStateOf(false) }

    // Automatically re-mask secret after 15 seconds for temporary reveal safety.
    LaunchedEffect(secretRevealed, item.id) {
        if (secretRevealed) {
            secondsRemaining = 15
            while (secondsRemaining > 0) {
                delay(1_000L)
                secondsRemaining -= 1
            }
            secretRevealed = false
        }
    }

    LaunchedEffect(copiedKey) {
        if (copiedKey != null) {
            delay(2_000L)
            copiedKey = null
        }
    }

    val secretFieldTitle = when (item.type) {
        VaultItemType.LOGIN -> "Password"
        VaultItemType.PASSKEY -> "Passkey Key Material (Phase 5 FIDO2)"
        VaultItemType.SECURE_NOTE -> "Protected Note Content"
        VaultItemType.IDENTITY -> "Protected Personal Details"
        VaultItemType.PAYMENT_CARD -> "Card Details"
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = colors.textPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = item.type.displayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("detail_favorite_button"),
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.cd_favorite_toggle),
                        tint = if (item.isFavorite) colors.warning else colors.textSecondary,
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("detail_edit_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit ${item.title}",
                        tint = colors.primary,
                    )
                }
            }

            // Title & origin hero card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.primaryMuted)
                            .border(1.dp, colors.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    ) {
                        Icon(
                            imageVector = itemTypeIcon(item.type),
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            modifier = Modifier.testTag("detail_item_title"),
                        )
                        val originLabel = item.normalizedDomain.ifBlank { item.type.displayLabel }
                        Text(
                            text = originLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                        )
                    }
                }
            }

            // Username / identifier card
            if (item.username.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (item.type == VaultItemType.LOGIN) "Username" else "Label / Holder",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                        }
                        Text(
                            text = item.username,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        CopyAction(
                            label = "Copy Username",
                            copied = copiedKey == "username",
                            onClick = {
                                copiedKey = "username"
                                onCopyField("Username", item.username, false)
                            },
                            testTag = "detail_copy_username_button",
                        )
                    }
                }
            }

            // Secret / password card (masked by default; obvious temporary reveal state)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (secretRevealed) colors.warning else colors.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = secretFieldTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (secretRevealed) colors.warning else colors.textSecondary,
                        )
                        if (secretRevealed) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.warningContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "Visible • hides in ${secondsRemaining}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.warning,
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceElevated)
                            .border(
                                1.dp,
                                if (secretRevealed) colors.warning.copy(alpha = 0.55f) else colors.outlineSubtle,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = if (secretRevealed) {
                                item.secret.ifEmpty { "No secret saved" }
                            } else {
                                "••••••••••••••••"
                            },
                            style = VaultMonoBodyStyle,
                            color = colors.textPrimary,
                            modifier = Modifier.testTag("detail_secret_value"),
                        )
                    }

                    AnimatedVisibility(visible = secretRevealed) {
                        Text(
                            text = "Ensure nobody is viewing your screen. Secret re-masks automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.warning,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SecondaryButton(
                            text = if (secretRevealed) "Hide" else "Reveal",
                            leadingIcon = if (secretRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            onClick = { secretRevealed = !secretRevealed },
                            modifier = Modifier.weight(1f),
                            testTag = "detail_reveal_secret_button",
                        )
                        CopyAction(
                            label = "Copy",
                            copied = copiedKey == "secret",
                            onClick = {
                                copiedKey = "secret"
                                onCopyField(secretFieldTitle, item.secret, true)
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "detail_copy_secret_button",
                        )
                    }
                }
            }

            // Website & notes card
            if (item.website.isNotBlank() || item.notes.isNotBlank()) {
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
                        if (item.website.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Language,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Website",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary,
                                    )
                                }
                                Text(
                                    text = item.website,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                if (item.normalizedDomain.isNotBlank()) {
                                    Text(
                                        text = "Normalized host: ${item.normalizedDomain}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.primary,
                                    )
                                }
                            }
                        }

                        if (item.website.isNotBlank() && item.notes.isNotBlank()) {
                            HorizontalDivider(color = colors.outlineSubtle)
                        }

                        if (item.notes.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Notes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary,
                                )
                                Text(
                                    text = item.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }

            // Metadata card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Updated: ${item.updatedAtLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Created: ${item.createdAtLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SecondaryButton(
                    text = "Edit Item",
                    leadingIcon = Icons.Outlined.Edit,
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    testTag = "detail_edit_action_button",
                )
                DestructiveButton(
                    text = "Delete",
                    leadingIcon = Icons.Outlined.DeleteOutline,
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    testTag = "detail_delete_button",
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete \"${item.title}\"?",
            message = "This removes the item from your local Phase 1 mock vault immediately. Once local Wi-Fi sync is enabled in Phase 6, deletions will also propagate as tombstones to paired devices.",
            confirmLabel = "Delete Item",
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDeleteConfirmed()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
