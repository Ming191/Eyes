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
            title = "Tốc độ đọc",
            summary = "Điều chỉnh nhịp đọc để dễ nghe trong môi trường đông người.",
            valueLabel = "1.25x",
            contentDescription = "Thanh trượt tốc độ đọc 1.25 lần",
            sliderStateDescription = "Giá trị hiện tại 1.25 lần",
            value = 1.25f,
            valueRange = 0.5f..2.0f,
            onValueChange = {}
        )
    }
}
