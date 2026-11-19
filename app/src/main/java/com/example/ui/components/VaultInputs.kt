package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.PasswordStrength
import com.example.domain.model.StrengthLevel
import com.example.ui.theme.VaultMonoBodyStyle
import com.example.ui.theme.VaultTheme
import kotlin.math.roundToInt

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "vault_search_input",
) {
    val colors = VaultTheme.colors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("clear_search_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = colors.textSecondary,
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated.copy(alpha = 0.85f),
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            cursorColor = colors.primary,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    testTag: String = "secret_field_input",
    toggleTestTag: String = "secret_field_toggle",
) {
    val colors = VaultTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = true,
        isError = isError,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        placeholder = if (placeholder.isNotEmpty()) {
            {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary.copy(alpha = 0.7f),
                )
            }
        } else null,
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isError) colors.danger else colors.textSecondary,
                )
            }
        },
        trailingIcon = {
            IconButton(
                onClick = onToggleReveal,
                modifier = Modifier.testTag(toggleTestTag),
            ) {
                Icon(
                    imageVector = if (revealed) AranIcons.VisibilityOff else AranIcons.Visibility,
                    contentDescription = if (revealed) "Hide $label" else "Reveal $label",
                    tint = if (revealed) colors.primary else colors.textSecondary,
                )
            }
        },
        supportingText = supportingText?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) colors.danger else colors.textSecondary,
                )
            }
        },
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        textStyle = if (revealed) {
            VaultMonoBodyStyle.copy(color = colors.textPrimary)
        } else {
            MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary)
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = if (revealed) colors.warning else colors.primary,
            unfocusedBorderColor = if (revealed) colors.warning.copy(alpha = 0.7f) else colors.outline,
            errorBorderColor = colors.danger,
            errorContainerColor = colors.surface,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.primary,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
    testTag: String = "inline_error",
) {
    val colors = VaultTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.dangerContainer)
            .border(1.dp, colors.danger.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.danger,
        )
    }
}

@Composable
fun PasswordStrengthIndicator(
    strength: PasswordStrength,
    modifier: Modifier = Modifier,
) {
    val colors = VaultTheme.colors
    val accentColor = when (strength.level) {
        StrengthLevel.WEAK -> colors.danger
        StrengthLevel.FAIR -> colors.warning
        StrengthLevel.STRONG -> colors.primary
        StrengthLevel.VERY_STRONG -> colors.primary
    }
    val statusIcon = when (strength.level) {
        StrengthLevel.WEAK, StrengthLevel.FAIR -> Icons.Outlined.Warning
        StrengthLevel.STRONG, StrengthLevel.VERY_STRONG -> AranIcons.Shield
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Strength: ${strength.level.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                )
            }
            if (strength.isExactFormula && strength.entropyBits > 0.0) {
                Text(
                    text = "${strength.entropyBits.roundToInt()} bits",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }

        LinearProgressIndicator(
            progress = { strength.level.fraction },
            color = accentColor,
            trackColor = colors.outline,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )

        Text(
            text = strength.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
fun CategoryChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val colors = VaultTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.primaryMuted else colors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) colors.primary else colors.outline,
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { this.selected = selected }
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.primary else colors.textPrimary,
            )
            if (count != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) colors.primary.copy(alpha = 0.22f) else colors.surfaceElevated
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.primary else colors.textSecondary,
                    )
                }
            }
        }
    }
}
