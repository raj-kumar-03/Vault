package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.VaultCryptoMetadata
import com.example.domain.model.PasswordHealthAnalyzer
import com.example.domain.model.PasswordHealthReport
import com.example.domain.model.VaultItem
import com.example.ui.components.AranIcons
import com.example.ui.components.InlineAccentPill
import com.example.ui.theme.VaultTheme

private enum class DiagnosticBucket {
    WEAK,
    REUSED,
    SHORT,
    STRONG,
}

/**
 * Aran Security & Password Health screen.
 * Computes all diagnostics strictly offline on decrypted in-memory login records—zero cloud APIs.
 */
@Composable
fun PasswordHealthScreen(
    items: List<VaultItem>,
    cryptoMetadata: VaultCryptoMetadata,
    onBack: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
) {
    val colors = VaultTheme.colors
    val report = remember(items) { PasswordHealthAnalyzer.analyze(items) }
    var expandedBucket by remember { mutableStateOf<DiagnosticBucket?>(null) }
    var showOfflineMonitoringDialog by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("password_health_screen"),
        ) {
            // Top App Bar
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("health_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = colors.textPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Password Health",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            modifier = Modifier.testTag("health_screen_title"),
                        )
                        Text(
                            text = "Offline security audit across ${report.totalLogins} login(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            // Offline-Only Monitoring Banner Card (Aran hero banner style with inline lime pill)
            item(key = "monitoring_banner") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.32f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        colors.primaryMuted.copy(alpha = 0.55f),
                                        colors.surfaceElevated,
                                    )
                                )
                            )
                            .padding(18.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryMuted)
                                    .border(1.dp, colors.primary.copy(alpha = 0.45f), CircleShape),
                            ) {
                                Icon(
                                    imageVector = AranIcons.CloudOff,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "100% Offline Audit",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Aran never sends hashes or emails to external breach APIs. All checks run locally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                InlineAccentPill(
                                    text = "Why Offline",
                                    onClick = { showOfflineMonitoringDialog = true },
                                    testTag = "health_offline_policy_pill",
                                )
                            }
                        }
                    }
                }
            }

            // Account Protection Tile
            item(key = "protection_tile") {
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
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Vault Protection Active",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "${cryptoMetadata.kdfSummary} • AES-256-GCM (v${cryptoMetadata.formatVersion}) • SQLite",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.primaryMuted)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                            )
                        }
                    }
                }
            }

            // Segmented Password Health Meter Card
            item(key = "health_meter") {
                PasswordHealthMeterCard(report = report)
            }

            // Diagnostic Drill-Down Rows
            item(key = "diagnostics_header") {
                Text(
                    text = "Security Diagnostics",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item(key = "diag_weak") {
                DiagnosticDrillRow(
                    icon = Icons.Outlined.Warning,
                    accentColor = if (report.weakItems.isEmpty()) colors.primary else colors.danger,
                    title = "Weak passwords",
                    subtitle = if (report.weakItems.isEmpty()) {
                        "No weak logins detected"
                    } else {
                        "${report.weakItems.size} login(s) need a stronger password"
                    },
                    count = report.weakItems.size,
                    items = report.weakItems,
                    expanded = expandedBucket == DiagnosticBucket.WEAK,
                    onToggle = {
                        expandedBucket = if (expandedBucket == DiagnosticBucket.WEAK) null else DiagnosticBucket.WEAK
                    },
                    onOpenItem = onOpenItem,
                    testTag = "health_row_weak",
                )
            }

            item(key = "diag_reused") {
                DiagnosticDrillRow(
                    icon = Icons.Outlined.Refresh,
                    accentColor = if (report.reusedItems.isEmpty()) colors.primary else colors.warning,
                    title = "Reused passwords",
                    subtitle = if (report.reusedItems.isEmpty()) {
                        "Every login uses a unique password"
                    } else {
                        "${report.reusedItems.size} login(s) share an identical password"
                    },
                    count = report.reusedItems.size,
                    items = report.reusedItems,
                    expanded = expandedBucket == DiagnosticBucket.REUSED,
                    onToggle = {
                        expandedBucket = if (expandedBucket == DiagnosticBucket.REUSED) null else DiagnosticBucket.REUSED
                    },
                    onOpenItem = onOpenItem,
                    testTag = "health_row_reused",
                )
            }

            item(key = "diag_short") {
                DiagnosticDrillRow(
                    icon = Icons.Outlined.Lock,
                    accentColor = if (report.shortItems.isEmpty()) colors.primary else colors.warning,
                    title = "Short passwords (< 12 chars)",
                    subtitle = if (report.shortItems.isEmpty()) {
                        "All logins meet the 12-character baseline"
                    } else {
                        "${report.shortItems.size} login(s) are shorter than 12 characters"
                    },
                    count = report.shortItems.size,
                    items = report.shortItems,
                    expanded = expandedBucket == DiagnosticBucket.SHORT,
                    onToggle = {
                        expandedBucket = if (expandedBucket == DiagnosticBucket.SHORT) null else DiagnosticBucket.SHORT
                    },
                    onOpenItem = onOpenItem,
                    testTag = "health_row_short",
                )
            }

            item(key = "diag_strong") {
                DiagnosticDrillRow(
                    icon = Icons.Outlined.Check,
                    accentColor = colors.primary,
                    title = "Strong & unique",
                    subtitle = "${report.strongItems.size} login(s) meet strong local entropy thresholds",
                    count = report.strongItems.size,
                    items = report.strongItems,
                    expanded = expandedBucket == DiagnosticBucket.STRONG,
                    onToggle = {
                        expandedBucket = if (expandedBucket == DiagnosticBucket.STRONG) null else DiagnosticBucket.STRONG
                    },
                    onOpenItem = onOpenItem,
                    testTag = "health_row_strong",
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showOfflineMonitoringDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineMonitoringDialog = false },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Zero-Cloud Security Audit") },
            text = {
                Text(
                    "Aran is intentionally built without cloud accounts or remote breach-monitoring APIs. " +
                        "All password strength, length, and reuse diagnostics run exclusively on-device in memory while your vault is unlocked, ensuring zero metadata ever leaves your phone."
                )
            },
            confirmButton = {
                TextButton(onClick = { showOfflineMonitoringDialog = false }) {
                    Text("Got it", color = colors.primary)
                }
            },
        )
    }
}

@Composable
fun PasswordHealthSegmentedBar(
    report: PasswordHealthReport,
    modifier: Modifier = Modifier,
) {
    val colors = VaultTheme.colors
    val strongW = report.strongFraction.coerceAtLeast(0f)
    val fairW = report.fairFraction.coerceAtLeast(0f)
    val weakW = report.weakFraction.coerceAtLeast(0f)
    val totalW = strongW + fairW + weakW

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(colors.outlineSubtle),
    ) {
        if (totalW <= 0f) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(colors.primary),
            )
        } else {
            if (strongW > 0f) {
                Box(
                    modifier = Modifier
                        .weight(strongW)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colors.primary),
                )
            }
            if (fairW > 0f) {
                Box(
                    modifier = Modifier
                        .weight(fairW)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colors.warning),
                )
            }
            if (weakW > 0f) {
                Box(
                    modifier = Modifier
                        .weight(weakW)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colors.danger),
                )
            }
        }
    }
}

@Composable
private fun PasswordHealthMeterCard(
    report: PasswordHealthReport,
) {
    val colors = VaultTheme.colors
    val scoreColor = when {
        report.overallScorePercent >= 80 -> colors.primary
        report.overallScorePercent >= 55 -> colors.warning
        else -> colors.danger
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Password Health",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Distribution across strong, fair, and weak logins",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Text(
                    text = "${report.overallScorePercent}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = scoreColor,
                    modifier = Modifier.testTag("health_score_value"),
                )
            }

            PasswordHealthSegmentedBar(report = report)

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HealthLegendItem(label = "Strong", count = report.strongItems.size, dotColor = colors.primary)
                HealthLegendItem(label = "Fair", count = report.fairItems.size, dotColor = colors.warning)
                HealthLegendItem(label = "Weak", count = report.weakItems.size, dotColor = colors.danger)
            }
        }
    }
}

@Composable
private fun HealthLegendItem(
    label: String,
    count: Int,
    dotColor: Color,
) {
    val colors = VaultTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun DiagnosticDrillRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    count: Int,
    items: List<VaultItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    testTag: String,
) {
    val colors = VaultTheme.colors

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) accentColor.copy(alpha = 0.55f) else colors.outlineSubtle,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = items.isNotEmpty(), onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
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
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                    )
                }

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded && items.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = colors.outlineSubtle)
                    items.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenItem(item) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("health_item_${item.id}"),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
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
                            }
                            Text(
                                text = "Inspect",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primary,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(color = colors.outlineSubtle.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
