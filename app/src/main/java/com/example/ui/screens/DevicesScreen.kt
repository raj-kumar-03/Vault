package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.DeviceStatus
import com.example.domain.model.PairedDevice
import com.example.ui.components.AranIcons
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.DeviceCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.VaultMonoDisplayStyle
import com.example.ui.theme.VaultTheme

@Composable
fun DevicesScreen(
    devices: List<PairedDevice>,
    onRevokeDevice: (String) -> Unit,
) {
    val colors = VaultTheme.colors
    var deviceToRevoke by remember { mutableStateOf<PairedDevice?>(null) }
    var showPairingPreview by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Paired Devices",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Local Wi-Fi device sync without any cloud relay",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            item(key = "phase_notice") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Mocked Device States • Phase 1",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.warning,
                            )
                            Text(
                                text = "No network discovery, QR key exchange, or synchronization is active yet. Ephemeral X25519 pairing with Short Authentication String verification arrives in Phase 6.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            items(
                items = devices,
                key = { it.id },
            ) { device ->
                DeviceCard(
                    device = device,
                    onRevokeClick = if (device.status == DeviceStatus.ACTIVE) {
                        { deviceToRevoke = device }
                    } else {
                        null
                    },
                )
            }

            item(key = "pair_action") {
                Spacer(modifier = Modifier.height(6.dp))
                PrimaryButton(
                    text = "Preview Device Pairing Flow",
                    leadingIcon = AranIcons.Devices,
                    onClick = { showPairingPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "devices_pair_preview_button",
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    deviceToRevoke?.let { target ->
        ConfirmationDialog(
            title = "Revoke ${target.friendlyName}?",
            message = "In Phase 1 this updates mock device state only. In production (Phase 6), revoking a device invalidates its sync session keys so it can no longer synchronize vault updates over local Wi-Fi.",
            confirmLabel = "Revoke Device",
            isDestructive = true,
            onConfirm = {
                onRevokeDevice(target.id)
                deviceToRevoke = null
            },
            onDismiss = { deviceToRevoke = null },
        )
    }

    if (showPairingPreview) {
        PairingPreviewSheet(onDismiss = { showPairingPreview = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingPreviewSheet(
    onDismiss: () -> Unit,
) {
    val colors = VaultTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.warningContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Non-Functional Preview • Ships in Phase 6",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                )
            }

            Text(
                text = "Pair New Device",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            // Deterministic geometric placeholder matrix representing planned QR handshake
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(164.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEAF4EE))
                    .border(2.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                PlaceholderQrMatrix(
                    moduleColor = Color(0xFF0B1210),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Sample Short Authentication String (SAS)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "7F3K-9Q2M",
                        style = VaultMonoDisplayStyle,
                        color = colors.primary,
                    )
                    Text(
                        text = "Phase 6 pairing will use ephemeral X25519 + HKDF-SHA256. The master password and vault encryption key are never embedded inside QR codes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = AranIcons.Schedule,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Planned session expiry: 03:00 (preview only)",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }

            SecondaryButton(
                text = "Close Preview",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                testTag = "pairing_preview_close_button",
            )
        }
    }
}

@Composable
private fun PlaceholderQrMatrix(
    moduleColor: Color,
    modifier: Modifier = Modifier,
) {
    val gridSize = 11
    val pattern = remember {
        listOf(
            "11111010111",
            "10001001101",
            "10101010101",
            "10001011001",
            "11111001111",
            "00000010000",
            "10110101101",
            "01001010010",
            "11111011011",
            "10001001100",
            "11111010111",
        )
    }
    Canvas(modifier = modifier) {
        val cellW = size.width / gridSize
        val cellH = size.height / gridSize
        for (row in 0 until gridSize) {
            val line = pattern[row]
            for (col in 0 until gridSize) {
                if (line[col] == '1') {
                    drawRect(
                        color = moduleColor,
                        topLeft = Offset(col * cellW, row * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f),
                    )
                }
            }
        }
    }
}
