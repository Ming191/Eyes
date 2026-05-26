package com.example.eyes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.ui.theme.EyesTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        EmergencyNumberSection(
            emergencyPhoneNumber = state.emergencyPhoneNumber,
            onEmergencyPhoneNumberChange = viewModel::setEmergencyPhoneNumber,
            onPresetSelected = viewModel::setEmergencyPreset
        )

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

@Composable
private fun EmergencyNumberSection(
    emergencyPhoneNumber: String,
    onEmergencyPhoneNumberChange: (String) -> Unit,
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Khu vực cài đặt số gọi khẩn cấp" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Gọi khẩn cấp",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Chọn số dùng cho nút gọi khẩn cấp ở trang chủ. Ứng dụng chỉ mở trình gọi, không tự gọi ngay.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                contentDescription = "Chọn số dùng cho nút gọi khẩn cấp ở trang chủ. Ứng dụng chỉ mở trình gọi, không tự gọi ngay."
            }
        )
        OutlinedTextField(
            value = emergencyPhoneNumber,
            onValueChange = onEmergencyPhoneNumberChange,
            label = { Text("Số khẩn cấp") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics {
                    contentDescription = "Ô nhập số gọi khẩn cấp hiện tại $emergencyPhoneNumber"
                }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Các số khẩn cấp nhanh" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmergencyPresetButton(
                label = "115 Cấp cứu",
                number = "115",
                onPresetSelected = onPresetSelected,
                modifier = Modifier.weight(1f)
            )
            EmergencyPresetButton(
                label = "113 Công an",
                number = "113",
                onPresetSelected = onPresetSelected,
                modifier = Modifier.weight(1f)
            )
            EmergencyPresetButton(
                label = "114 Cứu hỏa",
                number = "114",
                onPresetSelected = onPresetSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmergencyPresetButton(
    label: String,
    number: String,
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = { onPresetSelected(number) },
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "Chọn $label làm số gọi khẩn cấp" }
    ) {
        Text(label)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    EyesTheme(dynamicColor = false) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsScreenContentPreview()
        }
    }
}

@Composable
private fun SettingsScreenContentPreview() {
    val state = SettingsUiState(
        ttsSpeed = 1.1f,
        alertSensitivity = 0.6f,
        autoTranslateEnglishOcrToVietnamese = true,
        emergencyPhoneNumber = "115"
    )

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
            valueLabel = "${"%.2f".format(state.ttsSpeed)}x",
            contentDescription = "Thanh trượt tốc độ đọc ${"%.2f".format(state.ttsSpeed)} lần",
            sliderStateDescription = "Giá trị hiện tại ${"%.2f".format(state.ttsSpeed)} lần",
            value = state.ttsSpeed,
            valueRange = 0.5f..2.0f,
            onValueChange = {}
        )
        SettingSliderCard(
            title = "Độ nhạy cảnh báo",
            summary = "Tăng khi cần phản hồi sớm hơn về vật cản, giảm khi cần ít thông báo hơn.",
            valueLabel = "${(state.alertSensitivity * 100).toInt()}%",
            contentDescription = "Thanh trượt độ nhạy cảnh báo ${(state.alertSensitivity * 100).toInt()} phần trăm",
            sliderStateDescription = "Giá trị hiện tại ${(state.alertSensitivity * 100).toInt()} phần trăm",
            value = state.alertSensitivity,
            valueRange = 0f..1f,
            onValueChange = {}
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tự động dịch EN -> VI khi OCR",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = state.autoTranslateEnglishOcrToVietnamese,
                onCheckedChange = {}
            )
        }
        EmergencyNumberSection(
            emergencyPhoneNumber = state.emergencyPhoneNumber,
            onEmergencyPhoneNumberChange = {},
            onPresetSelected = {}
        )
    }
}
