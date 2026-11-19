package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.domain.model.GeneratorConfig
import com.example.domain.model.GeneratorMode
import com.example.ui.components.AranIcons
import com.example.ui.components.CategoryChip
import com.example.ui.components.CopyAction
import com.example.ui.components.PasswordStrengthIndicator
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.state.GeneratorUiState
import com.example.ui.theme.VaultMonoDisplayStyle
import com.example.ui.theme.VaultMonoLabelStyle
import com.example.ui.theme.VaultTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun GeneratorScreen(
    state: GeneratorUiState,
    onModeChange: (GeneratorMode) -> Unit,
    onConfigChange: ((GeneratorConfig) -> GeneratorConfig) -> Unit,
    onRegenerate: () -> Unit,
    onCopyGenerated: (String) -> Unit,
    onUseInNewLogin: (String) -> Unit,
) {
    val colors = VaultTheme.colors
    var copied by remember(state.generatedValue) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000L)
            copied = false
        }
    }

    val styledPassword = remember(state.generatedValue, colors) {
        buildAnnotatedString {
            state.generatedValue.forEach { ch ->
                val tint = when {
                    ch.isDigit() -> colors.primary
                    !ch.isLetterOrDigit() -> colors.warning
                    else -> colors.textPrimary
                }
                withStyle(SpanStyle(color = tint)) {
                    append(ch)
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Password Generator",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Local SecureRandom generation with transparent entropy math",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            // Mode selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GeneratorMode.entries.forEach { mode ->
                    CategoryChip(
                        label = mode.label,
                        count = null,
                        selected = state.config.mode == mode,
                        onClick = { onModeChange(mode) },
                        modifier = Modifier.weight(1f),
                        testTag = "generator_mode_${mode.name.lowercase()}",
                    )
                }
            }

            // Generated output display card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 22.dp),
                    ) {
                        Text(
                            text = styledPassword,
                            style = VaultMonoDisplayStyle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("generator_output_text"),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CopyAction(
                            label = "Copy",
                            copied = copied,
                            onClick = {
                                copied = true
                                onCopyGenerated(state.generatedValue)
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "generator_copy_button",
                        )
                        PrimaryButton(
                            text = "Generate",
                            leadingIcon = Icons.Outlined.Refresh,
                            onClick = onRegenerate,
                            modifier = Modifier.weight(1f),
                            testTag = "generator_regenerate_button",
                        )
                    }

                    PasswordStrengthIndicator(strength = state.strength)
                }
            }

            // Options card
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
                    Text(
                        text = "Options",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )

                    if (state.config.mode == GeneratorMode.PASSWORD) {
                        StepperRow(
                            label = "Length",
                            value = state.config.length,
                            min = 8,
                            max = 64,
                            onDecrement = {
                                onConfigChange { it.copy(length = (it.length - 1).coerceAtLeast(8)) }
                            },
                            onIncrement = {
                                onConfigChange { it.copy(length = (it.length + 1).coerceAtMost(64)) }
                            },
                        )

                        Slider(
                            value = state.config.length.toFloat(),
                            onValueChange = { raw ->
                                onConfigChange { it.copy(length = raw.roundToInt().coerceIn(8, 64)) }
                            },
                            valueRange = 8f..64f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.outline,
                            ),
                            modifier = Modifier.testTag("generator_length_slider"),
                        )

                        HorizontalDivider(color = colors.outlineSubtle)

                        OptionToggleRow(
                            title = "Uppercase letters",
                            subtitle = "A – Z (26 characters)",
                            checked = state.config.includeUppercase,
                            onCheckedChange = { checked -> onConfigChange { it.copy(includeUppercase = checked) } },
                            testTag = "generator_toggle_uppercase",
                        )
                        OptionToggleRow(
                            title = "Lowercase letters",
                            subtitle = "a – z (26 characters)",
                            checked = state.config.includeLowercase,
                            onCheckedChange = { checked -> onConfigChange { it.copy(includeLowercase = checked) } },
                            testTag = "generator_toggle_lowercase",
                        )
                        OptionToggleRow(
                            title = "Numbers",
                            subtitle = "0 – 9 (10 digits)",
                            checked = state.config.includeNumbers,
                            onCheckedChange = { checked -> onConfigChange { it.copy(includeNumbers = checked) } },
                            testTag = "generator_toggle_numbers",
                        )
                        OptionToggleRow(
                            title = "Symbols",
                            subtitle = "!@#\$%^&*… punctuation characters",
                            checked = state.config.includeSymbols,
                            onCheckedChange = { checked -> onConfigChange { it.copy(includeSymbols = checked) } },
                            testTag = "generator_toggle_symbols",
                        )
                        HorizontalDivider(color = colors.outlineSubtle)
                        OptionToggleRow(
                            title = "Exclude ambiguous characters",
                            subtitle = "Omits I, l, 1, O, 0, | for easier manual entry",
                            checked = state.config.excludeAmbiguous,
                            onCheckedChange = { checked -> onConfigChange { it.copy(excludeAmbiguous = checked) } },
                            testTag = "generator_toggle_ambiguous",
                        )
                    } else {
                        StepperRow(
                            label = "Word count",
                            value = state.config.wordCount,
                            min = 3,
                            max = 8,
                            onDecrement = {
                                onConfigChange { it.copy(wordCount = (it.wordCount - 1).coerceAtLeast(3)) }
                            },
                            onIncrement = {
                                onConfigChange { it.copy(wordCount = (it.wordCount + 1).coerceAtMost(8)) }
                            },
                        )

                        Text(
                            text = "Word separator",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val separators = listOf("-" to "Hyphen", "." to "Dot", "_" to "Underscore", " " to "Space")
                            separators.forEach { (sep, label) ->
                                CategoryChip(
                                    label = label,
                                    count = null,
                                    selected = state.config.wordSeparator == sep,
                                    onClick = { onConfigChange { it.copy(wordSeparator = sep) } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        HorizontalDivider(color = colors.outlineSubtle)

                        OptionToggleRow(
                            title = "Capitalize words",
                            subtitle = "Makes word boundaries clearer when typing",
                            checked = state.config.capitalizeWords,
                            onCheckedChange = { checked -> onConfigChange { it.copy(capitalizeWords = checked) } },
                            testTag = "generator_toggle_capitalize",
                        )

                        OptionToggleRow(
                            title = "Include two-digit number",
                            subtitle = "Appends a random number to satisfy legacy site rules",
                            checked = state.config.includePassphraseNumber,
                            onCheckedChange = { checked ->
                                onConfigChange { it.copy(includePassphraseNumber = checked) }
                            },
                            testTag = "generator_toggle_passphrase_number",
                        )
                    }
                }
            }

            SecondaryButton(
                text = "Use in New Login",
                leadingIcon = Icons.Outlined.Add,
                onClick = { onUseInNewLogin(state.generatedValue) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "generator_use_in_login_button",
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = VaultTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.outline),
            ) {
                IconButton(
                    onClick = onDecrement,
                    enabled = value > min,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = AranIcons.Minus,
                        contentDescription = "Decrease $label",
                        tint = if (value > min) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = value.toString(),
                    style = VaultMonoLabelStyle,
                    color = colors.primary,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.outline),
            ) {
                IconButton(
                    onClick = onIncrement,
                    enabled = value < max,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Increase $label",
                        tint = if (value < max) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val colors = VaultTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceElevated,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}
