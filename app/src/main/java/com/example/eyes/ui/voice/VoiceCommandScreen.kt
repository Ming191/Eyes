package com.example.eyes.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.system.SttErrorReason
import com.example.eyes.system.SttState
import com.example.eyes.ui.theme.EyesTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoiceCommandScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateBackHome: () -> Unit,
    viewModel: VoiceCommandViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Greet + auto-start STT when the screen first appears.
    LaunchedEffect(Unit) {
        viewModel.onScreenShown()
    }

    // Consume one-shot navigation events from the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.navigation.collect { target ->
            when (target) {
                VoiceNavigationTarget.Camera -> onNavigateToCamera()
                VoiceNavigationTarget.Map -> onNavigateToMap()
                VoiceNavigationTarget.Home -> onNavigateBackHome()
            }
        }
    }

    VoiceCommandContent(
        state = state,
        onMicTap = viewModel::startListening,
        onStopTap = viewModel::stopListening
    )
}

@Composable
private fun VoiceCommandContent(
    state: VoiceCommandUiState,
    onMicTap: () -> Unit,
    onStopTap: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(20.dp)
            .semantics { contentDescription = "Màn hình ra lệnh bằng giọng nói" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ra lệnh bằng giọng nói",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() }
        )

        Text(
            text = "Hãy nói một câu lệnh tiếng Việt sau khi nghe tín hiệu.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StatusBlock(state = state)

        Spacer(modifier = Modifier.height(8.dp))

        MicButton(
            sttState = state.sttState,
            onMicTap = onMicTap,
            onStopTap = onStopTap
        )

        Spacer(modifier = Modifier.height(8.dp))

        AvailableCommandsCard(expanded = state.helpExpanded)
    }
}

@Composable
private fun StatusBlock(state: VoiceCommandUiState) {
    val statusText = statusLabelFor(state.sttState)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Trạng thái nhận diện: $statusText"
                liveRegion = LiveRegionMode.Polite
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (state.partialText.isNotEmpty()) {
                Text(
                    text = "\"${state.partialText}...\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.finalText.isNotEmpty()) {
                Text(
                    text = "Đã nghe: ${state.finalText}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.lastCommand?.let { command ->
                Text(
                    text = "Lệnh: ${commandLabelFor(command)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MicButton(
    sttState: SttState,
    onMicTap: () -> Unit,
    onStopTap: () -> Unit
) {
    val isListening = sttState is SttState.Listening
    val isProcessing = sttState is SttState.Processing

    val containerColor = when {
        isListening -> MaterialTheme.colorScheme.errorContainer
        isProcessing -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isListening -> MaterialTheme.colorScheme.onErrorContainer
        isProcessing -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val description = when (sttState) {
        SttState.Idle -> "Nút bắt đầu nói lệnh. Bấm rồi nói."
        SttState.Listening -> "Nút dừng nghe. Đang nghe lệnh."
        SttState.Processing -> "App đang xử lý câu nói. Vui lòng đợi."
        is SttState.Error -> "Nút thử lại sau khi gặp lỗi."
    }

    Button(
        onClick = {
            when (sttState) {
                SttState.Listening -> onStopTap()
                SttState.Processing -> Unit
                else -> onMicTap()
            }
        },
        enabled = !isProcessing,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = CircleShape,
        modifier = Modifier
            .size(160.dp)
            .semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun AvailableCommandsCard(expanded: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                contentDescription = "Danh sách các lệnh khả dụng."
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Các lệnh khả dụng",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { heading() }
            )
            CommandLine("Đọc giúp tôi", "đọc văn bản trước camera")
            CommandLine("Trước mặt có gì", "mô tả khung cảnh")
            CommandLine("Tờ tiền này bao nhiêu", "nhận diện tiền")
            CommandLine("Có vật cản không", "phát hiện vật cản")
            CommandLine("Đi đến <địa điểm>", "dẫn đường")
            CommandLine("Đọc lại", "nghe lại câu vừa rồi")
            CommandLine("Dừng", "dừng và về trang chủ")
            CommandLine("Trợ giúp", "nghe danh sách lệnh")
        }
    }
}

@Composable
private fun CommandLine(command: String, action: String) {
    Text(
        text = "• \"$command\" — $action",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.semantics {
            contentDescription = "Lệnh: $command. Tác dụng: $action"
        }
    )
}

private fun statusLabelFor(state: SttState): String = when (state) {
    SttState.Idle -> "Sẵn sàng"
    SttState.Listening -> "Đang nghe..."
    SttState.Processing -> "Đang xử lý..."
    is SttState.Error -> when (state.reason) {
        SttErrorReason.Network -> "Lỗi mạng"
        SttErrorReason.NoMatch -> "Không nghe thấy"
        SttErrorReason.Audio -> "Lỗi micro"
        SttErrorReason.PermissionDenied -> "Thiếu quyền micro"
        SttErrorReason.NotAvailable -> "Không hỗ trợ"
        is SttErrorReason.Unknown -> "Lỗi không xác định"
    }
}

private fun commandLabelFor(command: VoiceCommand): String = when (command) {
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
private fun VoiceCommandScreenPreview() {
    EyesTheme(dynamicColor = false) {
        VoiceCommandContent(
            state = VoiceCommandUiState(
                sttState = SttState.Idle,
                finalText = "đọc giúp tôi",
                lastCommand = VoiceCommand.ReadText
            ),
            onMicTap = {},
            onStopTap = {}
        )
    }
}