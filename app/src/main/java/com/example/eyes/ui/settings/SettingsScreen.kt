package com.example.eyes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.system.SttErrorReason
import com.example.eyes.system.SttState
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

        VoiceTestCard(
            state = state,
            onStart = viewModel::startVoiceTest,
            onStop = viewModel::stopVoiceTest,
            onReset = viewModel::resetVoiceTest
        )
    }
}

/**
 * Dev-only card that exercises the SttService + CommandParser pipeline.
 * Will be removed once the Voice screen lands in a follow-up commit.
 */
@Composable
private fun VoiceTestCard(
    state: SettingsUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit
) {
    val statusText = remember(state.sttState) { statusLabelFor(state.sttState) }
    val commandText = remember(state.parsedCommand) { commandLabelFor(state.parsedCommand) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                contentDescription = "Khu vực thử nghiệm nhận lệnh bằng giọng nói. Dành cho phát triển."
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Thử lệnh bằng giọng nói (dev)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Bấm bắt đầu, nói một lệnh tiếng Việt như \"đọc giúp tôi\", " +
                        "\"trước mặt có gì\", \"tiền này bao nhiêu\", hoặc \"đi đến hồ Gươm\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Trạng thái: $statusText",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics {
                    contentDescription = "Trạng thái nhận diện giọng nói: $statusText"
                    liveRegion = LiveRegionMode.Polite
                }
            )

            if (state.partialText.isNotEmpty()) {
                Text(
                    text = "Đang nghe: ${state.partialText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }

            if (state.finalText.isNotEmpty()) {
                Text(
                    text = "Đã nghe: ${state.finalText}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "Câu nói nhận được: ${state.finalText}"
                    }
                )
            }

            if (commandText.isNotEmpty()) {
                Text(
                    text = "Lệnh: $commandText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            VoiceTestActionRow(
                sttState = state.sttState,
                onStart = onStart,
                onStop = onStop,
                onReset = onReset
            )
        }
    }
}

@Composable
private fun VoiceTestActionRow(
    sttState: SttState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit
) {
    when (sttState) {
        SttState.Idle -> {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .semantics {
                        contentDescription = "Nút bắt đầu nói lệnh"
                    }
            ) {
                Text("Bắt đầu nói lệnh")
            }
        }

        SttState.Listening -> {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .semantics {
                        contentDescription = "Nút dừng nghe. App đang nghe."
                    }
            ) {
                Text("Đang nghe — chạm để dừng")
            }
        }

        SttState.Processing -> {
            OutlinedButton(
                onClick = { /* no-op while processing */ },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .semantics {
                        contentDescription = "App đang xử lý câu nói. Vui lòng đợi."
                    }
            ) {
                Text("Đang xử lý...")
            }
        }

        is SttState.Error -> {
            Button(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .semantics {
                        contentDescription = "Nút thử lại sau khi gặp lỗi"
                    }
            ) {
                Text("Thử lại")
            }
        }
    }
}

private fun statusLabelFor(state: SttState): String = when (state) {
    SttState.Idle -> "Sẵn sàng"
    SttState.Listening -> "Đang nghe"
    SttState.Processing -> "Đang xử lý"
    is SttState.Error -> when (state.reason) {
        SttErrorReason.Network -> "Lỗi mạng"
        SttErrorReason.NoMatch -> "Không nghe thấy"
        SttErrorReason.Audio -> "Lỗi micro"
        SttErrorReason.PermissionDenied -> "Thiếu quyền micro"
        SttErrorReason.NotAvailable -> "Không hỗ trợ"
        is SttErrorReason.Unknown -> "Lỗi không xác định"
    }
}

private fun commandLabelFor(command: VoiceCommand?): String = when (command) {
    null -> ""
    VoiceCommand.ReadText -> "Đọc văn bản"
    VoiceCommand.DescribeScene -> "Mô tả khung cảnh"
    VoiceCommand.RecognizeCurrency -> "Nhận diện tiền"
    VoiceCommand.DetectObstacle -> "Phát hiện vật cản"
    is VoiceCommand.Navigate -> "Dẫn đường: ${command.destination}"
    VoiceCommand.Repeat -> "Đọc lại"
    VoiceCommand.Stop -> "Dừng"
    VoiceCommand.Help -> "Trợ giúp"
    is VoiceCommand.Unknown -> "Chưa rõ — \"${command.rawText}\""
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    EyesTheme(dynamicColor = false) {
        VoiceTestCardPreview()
    }
}

@Composable
private fun VoiceTestCardPreview() {
    val state = SettingsUiState(
        ttsSpeed = 1.1f,
        alertSensitivity = 0.6f,
        autoTranslateEnglishOcrToVietnamese = true,
        sttState = SttState.Idle,
        partialText = "",
        finalText = "đọc giúp tôi",
        parsedCommand = VoiceCommand.ReadText
    )
    VoiceTestCard(state = state, onStart = {}, onStop = {}, onReset = {})
}
