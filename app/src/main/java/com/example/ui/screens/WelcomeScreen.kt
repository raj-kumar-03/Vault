package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.VerifiedUser
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
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.ShieldEmblem
import com.example.ui.theme.VaultTheme

@Composable
fun WelcomeScreen(
    isBusy: Boolean = false,
    onCreateVault: () -> Unit,
    onExploreDemoVault: () -> Unit,
) {
    val colors = VaultTheme.colors
    var activeSheet by remember { mutableStateOf<EducationSheetMode?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Atmospheric Midnight Emerald alpine backdrop blended smoothly into background
        Image(
            painter = painterResource(id = R.drawable.img_welcome_emerald_peaks_1795093219314),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (colors.isDark) 0.78f else 0.16f),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to colors.background.copy(alpha = 0.86f),
                        0.32f to colors.background.copy(alpha = 0.35f),
                        0.58f to colors.background.copy(alpha = 0.72f),
                        0.80f to colors.background.copy(alpha = 0.96f),
                        1.0f to colors.background,
                    )
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.phase_one_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                ShieldEmblem(size = 76.dp, locked = true)

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(36.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, colors.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(18.dp),
                    ) {
                        WelcomePrincipleRow(
                            icon = Icons.Outlined.CloudOff,
                            title = "Offline & Local-First",
                            description = "No cloud account, no remote servers, and no mandatory internet connection.",
                        )
                        WelcomePrincipleRow(
                            icon = Icons.Outlined.Key,
                            title = "Unrecoverable Without Your Keys",
                            description = "Losing both your master password and recovery material will make a real vault unrecoverable.",
                        )
                        WelcomePrincipleRow(
                            icon = Icons.Outlined.VerifiedUser,
                            title = "Phase 2 Crypto Active (In-Memory)",
                            description = "Records are encrypted with Argon2id, HKDF-SHA256, and AES-256-GCM with AAD. SQLite disk persistence arrives in Phase 3.",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                PrimaryButton(
                    text = "Create Vault",
                    onClick = onCreateVault,
                    enabled = !isBusy,
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

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = { activeSheet = EducationSheetMode.RESTORE_COMING_SOON },
                        modifier = Modifier.testTag("welcome_restore_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Restore,
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
            }
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
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primaryMuted.copy(alpha = 0.75f)),
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
