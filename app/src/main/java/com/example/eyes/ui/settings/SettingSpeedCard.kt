package com.example.eyes.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.eyes.R
import com.example.eyes.ui.blind.BlindDragAdjustment
import com.example.eyes.ui.blind.blindFocusable
import com.example.eyes.ui.theme.EyesTheme

data class SettingSpeedPreset(
    val focusId: String,
    val label: String,
    val contentDescription: String,
    val selected: Boolean,
    val onSelect: () -> Unit
)

@Composable
fun SettingSpeedCard(
    title: String,
    summary: String? = null,
    valueLabel: String,
    contentDescription: String,
    sliderContentDescription: String,
    sliderStateDescription: String,
    sliderFocusId: String,
    sliderAdjustStartDescription: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseFocusId: String,
    increaseFocusId: String,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    presets: List<SettingSpeedPreset>,
    modifier: Modifier = Modifier
) {
    var dragStartValue by remember { mutableStateOf(value) }
    val dragValueRange = valueRange.endInclusive - valueRange.start

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onDecrease,
                    enabled = canDecrease,
                    modifier = Modifier
                        .size(56.dp)
                        .optionalBlindFocusable(
                            id = decreaseFocusId,
                            label = decreaseContentDescription,
                            enabled = canDecrease,
                            onActivate = onDecrease
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = decreaseContentDescription
                    )
                }
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = onIncrease,
                    enabled = canIncrease,
                    modifier = Modifier
                        .size(56.dp)
                        .optionalBlindFocusable(
                            id = increaseFocusId,
                            label = increaseContentDescription,
                            enabled = canIncrease,
                            onActivate = onIncrease
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = increaseContentDescription
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    SpeedPresetButton(
                        preset = preset,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .semantics {
                        this.contentDescription = sliderContentDescription
                        this.stateDescription = sliderStateDescription
                        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                            current = value,
                            range = valueRange
                        )
                    }
                    .blindFocusable(
                        id = sliderFocusId,
                        label = sliderContentDescription,
                        onActivate = {},
                        activateLabel = sliderAdjustStartDescription,
                        adjustment = BlindDragAdjustment(
                            startLabel = sliderAdjustStartDescription,
                            onStart = { dragStartValue = value },
                            onDrag = { deltaFraction ->
                                onValueChange(
                                    (dragStartValue + deltaFraction * dragValueRange)
                                        .coerceIn(valueRange.start, valueRange.endInclusive)
                                )
                            },
                            onFinish = onValueChangeFinished
                        )
                    )
            )
        }
    }
}

@Composable
private fun SpeedPresetButton(
    preset: SettingSpeedPreset,
    modifier: Modifier = Modifier
) {
    val containerColor = if (preset.selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (preset.selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable { preset.onSelect() }
            .semantics {
                contentDescription = preset.contentDescription
                selected = preset.selected
            }
            .optionalBlindFocusable(
                id = preset.focusId,
                label = preset.contentDescription,
                enabled = true,
                onActivate = preset.onSelect
            ),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        tonalElevation = if (preset.selected) 2.dp else 0.dp
    ) {
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (preset.selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 18.dp)
        )
    }
}

private fun Modifier.optionalBlindFocusable(
    id: String,
    label: String,
    enabled: Boolean,
    onActivate: () -> Unit
): Modifier {
    return if (enabled) {
        blindFocusable(
            id = id,
            label = label,
            onActivate = onActivate
        )
    } else {
        this
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingSpeedCardPreview() {
    EyesTheme(dynamicColor = false) {
        SettingSpeedCard(
            title = stringResource(R.string.settings_tts_speed_title),
            summary = stringResource(R.string.settings_tts_speed_summary),
            valueLabel = "1.25x",
            contentDescription = stringResource(R.string.settings_tts_speed_selector_description, "1.25"),
            sliderContentDescription = stringResource(R.string.settings_tts_speed_slider_description, "1.25"),
            sliderStateDescription = stringResource(R.string.settings_slider_state_description, "1.25"),
            sliderFocusId = "preview_tts_speed_slider",
            sliderAdjustStartDescription = stringResource(R.string.settings_tts_speed_slider_adjust_start),
            value = 1.25f,
            valueRange = TTS_SPEED_RANGE,
            steps = TTS_SPEED_SLIDER_STEPS,
            onValueChange = {},
            onValueChangeFinished = {},
            onDecrease = {},
            onIncrease = {},
            decreaseFocusId = "preview_tts_speed_decrease",
            increaseFocusId = "preview_tts_speed_increase",
            decreaseContentDescription = stringResource(R.string.settings_tts_speed_decrease_description),
            increaseContentDescription = stringResource(R.string.settings_tts_speed_increase_description),
            canDecrease = true,
            canIncrease = true,
            presets = TTS_SPEED_PRESET_VALUES.map { preset ->
                SettingSpeedPreset(
                    focusId = "preview_tts_speed_preset_${preset.idSuffix}",
                    label = "${preset.label}x",
                    contentDescription = stringResource(R.string.settings_tts_speed_preset_description, preset.label),
                    selected = preset.speed == 1.25f,
                    onSelect = {}
                )
            }
        )
    }
}
