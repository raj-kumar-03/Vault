package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DevicePlatform
import com.example.domain.model.DeviceStatus
import com.example.domain.model.PairedDevice
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import com.example.ui.theme.VaultMonoLabelStyle
import com.example.ui.theme.VaultTheme

enum class PrimaryDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    VAULT(
        route = "vault",
        label = "Vault",
        selectedIcon = AranIcons.ShieldFilled,
        unselectedIcon = AranIcons.Shield,
        testTag = "nav_vault",
    ),
    GENERATOR(
        route = "generator",
        label = "Generator",
        selectedIcon = AranIcons.Key,
        unselectedIcon = AranIcons.Key,
        testTag = "nav_generator",
    ),
    DEVICES(
        route = "devices",
        label = "Devices",
        selectedIcon = AranIcons.Devices,
        unselectedIcon = AranIcons.Devices,
        testTag = "nav_devices",
    ),
    SETTINGS(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings",
    ),
}

@Composable
fun AppScaffold(
    currentDestination: PrimaryDestination,
    onSelectDestination: (PrimaryDestination) -> Unit,
    onLockVault: () -> Unit,
    snackbarHostState: SnackbarHostState,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (isExpandedWidth: Boolean) -> Unit,
) {
    val colors = VaultTheme.colors

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val useRail = maxWidth >= 640.dp
        val isExpandedWidth = maxWidth >= 840.dp

        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = colors.surface,
                    contentColor = colors.textPrimary,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 12.dp, bottom = 16.dp),
                        ) {
                            ShieldEmblem(size = 44.dp, locked = false)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    PrimaryDestination.entries.forEach { destination ->
                        val selected = destination == currentDestination
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onSelectDestination(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary,
                                indicatorColor = colors.primaryMuted,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary,
                            ),
                            modifier = Modifier.testTag(destination.testTag),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("rail_lock_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.cd_lock_vault),
                            tint = colors.textSecondary,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(colors.outlineSubtle),
                )

                Scaffold(
                    containerColor = colors.background,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    floatingActionButton = floatingActionButton,
                    modifier = Modifier.weight(1f),
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        content(isExpandedWidth)
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = colors.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = floatingActionButton,
                bottomBar = {
                    Column {
                        HorizontalDivider(thickness = 1.dp, color = colors.outlineSubtle)
                        NavigationBar(
                            containerColor = colors.surface,
                            contentColor = colors.textPrimary,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars,
                        ) {
                            PrimaryDestination.entries.forEach { destination ->
                                val selected = destination == currentDestination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { onSelectDestination(destination) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = destination.label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.primary,
                                        selectedTextColor = colors.primary,
                                        indicatorColor = colors.primaryMuted,
                                        unselectedIconColor = colors.textSecondary,
                                        unselectedTextColor = colors.textSecondary,
                                    ),
                                    modifier = Modifier.testTag(destination.testTag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    content(false)
                }
            }
        }
    }
}

@Composable
fun VaultItemRow(
    item: VaultItem,
    selected: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultTheme.colors
    val typeIcon = itemTypeIcon(item.type)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.surfaceHighest else colors.surfaceElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) colors.primary.copy(alpha = 0.75f) else colors.outlineSubtle,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("vault_item_${item.id}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.primaryMuted.copy(alpha = 0.85f))
                    .border(1.dp, colors.primary.copy(alpha = 0.35f), CircleShape),
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = item.type.displayLabel,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.maskedSubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.normalizedDomain.isNotEmpty() && item.type == VaultItemType.LOGIN) {
                    Text(
                        text = item.normalizedDomain,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("favorite_toggle_${item.id}"),
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Star else AranIcons.StarOutline,
                    contentDescription = if (item.isFavorite) "Remove ${item.title} from favorites" else "Add ${item.title} to favorites",
                    tint = if (item.isFavorite) colors.warning else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SecurityStatusRow(
    title: String,
    subtitle: String,
    badgeLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = VaultTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.primaryMuted.copy(alpha = 0.22f))
                .padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.primaryMuted),
            ) {
                Icon(
                    imageVector = AranIcons.ShieldFilled,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.outline, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: PairedDevice,
    onRevokeClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = VaultTheme.colors
    val platformIcon = when (device.platform) {
        DevicePlatform.ANDROID -> AranIcons.PhoneAndroid
        DevicePlatform.LINUX -> AranIcons.Laptop
        DevicePlatform.WINDOWS -> AranIcons.Laptop
    }
    val statusBg = when (device.status) {
        DeviceStatus.THIS_DEVICE -> colors.primaryMuted
        DeviceStatus.ACTIVE -> colors.primaryMuted.copy(alpha = 0.7f)
        DeviceStatus.REVOKED -> colors.dangerContainer
    }
    val statusText = when (device.status) {
        DeviceStatus.THIS_DEVICE -> colors.primary
        DeviceStatus.ACTIVE -> colors.primary
        DeviceStatus.REVOKED -> colors.danger
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.outlineSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_card_${device.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.dp, colors.outline, CircleShape),
                ) {
                    Icon(
                        imageVector = platformIcon,
                        contentDescription = device.platform.label,
                        tint = if (device.status == DeviceStatus.REVOKED) colors.textSecondary else colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.friendlyName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "${device.platform.label} • ${device.lastSyncLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = device.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusText,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Key ID: ${device.fingerprintPreview}",
                    style = VaultMonoLabelStyle,
                    color = colors.textSecondary,
                )

                if (onRevokeClick != null && device.status == DeviceStatus.ACTIVE) {
                    Surface(
                        onClick = onRevokeClick,
                        shape = CircleShape,
                        color = colors.dangerContainer,
                        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                            .testTag("revoke_device_${device.id}"),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "Revoke",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.danger,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VaultTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
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
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionTestTag: String = "empty_state_action",
) {
    val colors = VaultTheme.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outline, CircleShape),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                testTag = actionTestTag,
            )
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VaultTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("dialog_confirm_button"),
            ) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDestructive) colors.danger else colors.primary,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button"),
            ) {
                Text(
                    text = dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

fun itemTypeIcon(type: VaultItemType): ImageVector = when (type) {
    VaultItemType.LOGIN -> AranIcons.Key
    VaultItemType.PASSKEY -> AranIcons.Fingerprint
    VaultItemType.SECURE_NOTE -> AranIcons.Description
    VaultItemType.IDENTITY -> AranIcons.Badge
    VaultItemType.PAYMENT_CARD -> AranIcons.CreditCard
}
