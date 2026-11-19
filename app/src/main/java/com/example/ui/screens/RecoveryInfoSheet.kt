package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.components.AranIcons
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.VaultTheme

enum class EducationSheetMode {
    RECOVERY,
    RESTORE_COMING_SOON,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryInfoSheet(
    mode: EducationSheetMode,
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
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val title = when (mode) {
                EducationSheetMode.RECOVERY -> "How Local Recovery Works"
                EducationSheetMode.RESTORE_COMING_SOON -> "Restore Encrypted Backup"
            }
            val badge = when (mode) {
                EducationSheetMode.RECOVERY -> "Active • Phase 3"
                EducationSheetMode.RESTORE_COMING_SOON -> "Active • .svbk v1"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryMuted)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Text(
                text = "Aran operates completely offline with no cloud account, no password reset email, and no server holding your keys.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            InfoPointCard(
                icon = AranIcons.CloudOff,
                title = "No Cloud Reset",
                body = "Because nothing leaves your device without explicit local export, nobody—not even the app developers—can reset a forgotten master password.",
            )

            InfoPointCard(
                icon = AranIcons.Key,
                title = "Independent 256-bit Recovery Key",
                body = "A separate high-entropy SVRK recovery key wraps your Vault Encryption Key independently via HKDF-SHA256.",
            )

            InfoPointCard(
                icon = AranIcons.Restore,
                title = "Versioned .svbk Local Backups",
                body = "Export and restore SHA-256 verified, AES-256-GCM encrypted backup files offline without touching your vault until every record passes authentication.",
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.dangerContainer,
                border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "If both your master password and recovery material are lost, your encrypted vault will be permanently unrecoverable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            PrimaryButton(
                text = "Understood",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                testTag = "recovery_sheet_dismiss_button",
            )
        }
    }
}

@Composable
private fun InfoPointCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    val colors = VaultTheme.colors
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
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
