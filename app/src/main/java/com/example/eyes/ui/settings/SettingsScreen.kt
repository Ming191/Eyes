package com.example.eyes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.ui.theme.EyesTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .semantics { contentDescription = "Màn hình cài đặt" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Điều chỉnh phản hồi",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Tăng tốc độ đọc khi ở ngoài đường đông người, hoặc hạ độ nhạy nếu bạn muốn ít cảnh báo hơn trong không gian yên tĩnh.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingSliderCard(
            title = "Tốc độ đọc",
            summary = "Giữ nhịp đọc rõ ràng khi nghe qua loa ngoài hoặc tai nghe một bên.",
            valueLabel = "${"%.2f".format(state.ttsSpeed)}x",
            contentDescription = "Thanh trượt tốc độ đọc ${"%.2f".format(state.ttsSpeed)} lần",
            sliderStateDescription = "Giá trị hiện tại ${"%.2f".format(state.ttsSpeed)} lần",
            value = state.ttsSpeed,
            valueRange = 0.5f..2.0f,
            onValueChange = viewModel::setTtsSpeed
        )

        SettingSliderCard(
            title = "Độ nhạy cảnh báo",
            summary = "Tăng khi cần phản hồi sớm hơn về vật cản, giảm khi cần ít thông báo hơn.",
            valueLabel = "${(state.alertSensitivity * 100).toInt()}%",
            contentDescription = "Thanh trượt độ nhạy cảnh báo ${(state.alertSensitivity * 100).toInt()} phần trăm",
            sliderStateDescription = "Giá trị hiện tại ${(state.alertSensitivity * 100).toInt()} phần trăm",
            value = state.alertSensitivity,
            valueRange = 0f..1f,
            onValueChange = viewModel::setAlertSensitivity
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Tự động dịch tiếng Anh sang tiếng Việt khi OCR" },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tự động dịch EN -> VI khi OCR",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = state.autoTranslateEnglishOcrToVietnamese,
                onCheckedChange = viewModel::setAutoTranslateEnglishOcrToVietnamese,
                modifier = Modifier.semantics {
                    contentDescription = "Bật tắt tự động dịch tiếng Anh sang tiếng Việt khi OCR"
                }
            )
        }

        Button(
            onClick = { viewModel.previewFeedback(state) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .semantics {
                    contentDescription = "Nút nghe thử phản hồi bằng âm thanh và rung theo cài đặt hiện tại"
                }
        ) {
            Text("Nghe thử phản hồi")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    EyesTheme(dynamicColor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Điều chỉnh phản hồi",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Tăng tốc độ đọc khi ở ngoài đường đông người, hoặc hạ độ nhạy nếu bạn muốn ít cảnh báo hơn trong không gian yên tĩnh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingSliderCard(
                title = "Tốc độ đọc",
                summary = "Giữ nhịp đọc rõ ràng khi nghe qua loa ngoài hoặc tai nghe một bên.",
                valueLabel = "1.10x",
                contentDescription = "Thanh trượt tốc độ đọc 1.10 lần",
                sliderStateDescription = "Giá trị hiện tại 1.10 lần",
                value = 1.1f,
                valueRange = 0.5f..2.0f,
                onValueChange = {}
            )
            SettingSliderCard(
                title = "Độ nhạy cảnh báo",
                summary = "Tăng khi cần phản hồi sớm hơn về vật cản, giảm khi cần ít thông báo hơn.",
                valueLabel = "60%",
                contentDescription = "Thanh trượt độ nhạy cảnh báo 60 phần trăm",
                sliderStateDescription = "Giá trị hiện tại 60 phần trăm",
                value = 0.6f,
                valueRange = 0f..1f,
                onValueChange = {}
            )
        }
    }
}
