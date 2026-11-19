package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.VaultTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    testTag: String? = null,
) {
    val colors = VaultTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.surfaceElevated,
            disabledContentColor = colors.textSecondary.copy(alpha = 0.55f),
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier
            .heightIn(min = 52.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    testTag: String? = null,
) {
    val colors = VaultTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) colors.outline else colors.outlineSubtle,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceElevated.copy(alpha = 0.78f),
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.surface.copy(alpha = 0.4f),
            disabledContentColor = colors.textSecondary.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier
            .heightIn(min = 52.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (enabled) colors.primary else colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    testTag: String? = null,
) {
    val colors = VaultTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) colors.danger.copy(alpha = 0.55f) else colors.outlineSubtle,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.dangerContainer,
            contentColor = colors.danger,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.textSecondary.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier
            .heightIn(min = 52.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun InlineAccentPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val colors = VaultTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.primary,
        contentColor = colors.onPrimary,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(CircleShape),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.defaultMinSize(minHeight = 36.dp),
            ) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimary,
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
        }
    }
}

@Composable
fun CopyAction(
    label: String,
    copied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    testTag: String? = null,
) {
    val colors = VaultTheme.colors
    val containerColor = if (copied) colors.primaryMuted else colors.surfaceElevated
    val borderColor = if (copied) colors.primary.copy(alpha = 0.6f) else colors.outline
    val contentColor = if (copied) colors.primary else colors.textPrimary
    val description = if (copied) "$label copied" else "Copy $label"

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = description }
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        Row(
            modifier = if (compact) {
                Modifier
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (copied) Icons.Outlined.Check else AranIcons.ContentCopy,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (copied) "Copied" else label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
fun ShieldEmblem(
    size: Dp = 64.dp,
    locked: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = VaultTheme.colors
    val emblemDescription = stringResource(R.string.cd_app_emblem)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.primaryMuted.copy(alpha = 0.95f),
                        colors.surfaceElevated.copy(alpha = 0.9f),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = colors.primary.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .semantics { contentDescription = emblemDescription },
    ) {
        Icon(
            imageVector = AranIcons.ShieldFilled,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(size * 0.56f),
        )
        if (locked) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(size * 0.24f),
            )
        }
    }
}
