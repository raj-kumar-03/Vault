package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AranIcons
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.ShieldEmblem
import com.example.ui.theme.VaultTheme

@Composable
fun WelcomeScreen(
    isBusy: Boolean = false,
    onCreateVault: () -> Unit,
    onExploreDemoVault: () -> Unit,
    onRestoreBackup: () -> Unit = {},
) {
    val colors = VaultTheme.colors
    var activeSheet by remember { mutableStateOf<EducationSheetMode?>(null) }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Top Aran brand header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShieldEmblem(size = 38.dp, locked = true)
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.outline, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Phase 3 • Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Textured dark graphic hero card (Aran reference hero)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_emerald_peaks_1795093219314),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (colors.isDark) 0.55f else 0.28f),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colors.background.copy(alpha = 0.25f),
                                        colors.background.copy(alpha = 0.88f),
                                    )
                                )
                            ),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                    ) {
                        ShieldEmblem(size = 76.dp, locked = true)
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.surface.copy(alpha = 0.9f))
                                .border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Argon2id • AES-256-GCM • Local SQLite",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bold value proposition headline & supporting copy
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Generate strong passwords, audit vault health, and encrypt every record offline on your device—with zero cloud accounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Full-width Aran lime primary CTA pill with directional chevrons
            PrimaryButton(
                text = "Get Started",
                onClick = onCreateVault,
                enabled = !isBusy,
                trailingIcon = AranIcons.DoubleChevronRight,
                modifier = Modifier.fillMaxWidth(),
                testTag = "welcome_create_vault_button",
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryButton(
                text = if (isBusy) "Deriving Vault Key…" else "Explore Demo Vault",
                onClick = onExploreDemoVault,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                testTag = "welcome_demo_vault_button",
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onRestoreBackup,
                    enabled = !isBusy,
                    modifier = Modifier.testTag("welcome_restore_button"),
                ) {
                    Icon(
                        imageVector = AranIcons.Restore,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Restore Backup",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                }

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.outline,
                )

                TextButton(
                    onClick = { activeSheet = EducationSheetMode.RECOVERY },
                    modifier = Modifier.testTag("welcome_learn_recovery_button"),
                ) {
                    Text(
                        text = "Learn About Recovery",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Concise honest security guarantees card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    WelcomePrincipleRow(
                        icon = AranIcons.CloudOff,
                        title = "Offline & Local-First",
                        description = "No cloud account, no telemetry, and no mandatory internet connection.",
                    )
                    WelcomePrincipleRow(
                        icon = AranIcons.Key,
                        title = "Unrecoverable Without Your Keys",
                        description = "Your master password and independent recovery key are the sole keys to your vault.",
                    )
                    WelcomePrincipleRow(
                        icon = AranIcons.ShieldFilled,
                        title = "Authenticated SQLite & .svbk Backups",
                        description = "Per-record AES-256-GCM encryption with AAD and SHA-256 verified offline backups.",
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    activeSheet?.let { mode ->
        RecoveryInfoSheet(
            mode = mode,
            onDismiss = { activeSheet = null },
        )
    }
}

@Composable
private fun WelcomePrincipleRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val colors = VaultTheme.colors
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.primaryMuted.copy(alpha = 0.8f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
