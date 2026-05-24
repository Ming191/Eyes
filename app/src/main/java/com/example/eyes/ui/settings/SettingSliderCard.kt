package com.example.eyes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.eyes.R
import com.example.eyes.ui.theme.EyesTheme

@Composable
fun SettingSliderCard(
    title: String,
    summary: String,
    valueLabel: String,
    contentDescription: String,
    sliderStateDescription: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        this.contentDescription = contentDescription
                        this.stateDescription = sliderStateDescription
                        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                            current = value,
                            range = valueRange
                        )
                    }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingSliderCardPreview() {
    EyesTheme(dynamicColor = false) {
        SettingSliderCard(
            title = stringResource(R.string.settings_tts_speed_title),
            summary = stringResource(R.string.settings_tts_speed_summary),
            valueLabel = "1.25x",
            contentDescription = stringResource(R.string.settings_tts_speed_slider_description, "1.25"),
            sliderStateDescription = stringResource(R.string.settings_slider_state_description, "1.25"),
            value = 1.25f,
            valueRange = 0.5f..2.0f,
            onValueChange = {}
        )
    }
}
