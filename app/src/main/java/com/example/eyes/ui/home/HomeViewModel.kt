package com.example.eyes.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.example.eyes.system.SpeechOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeActionType {
    ScanAround,
    ReadTextQuick,
    ReadTextAccuracy,
    Navigate,
    Settings
}

@Immutable
data class HomeAction(
    val type: HomeActionType,
    val title: String,
    val description: String,
    val supportingLabel: String,
    val accessibilityLabel: String
)

@Immutable
data class HomeUiState(
    val welcomeTitle: String = "Hỗ trợ di chuyển rõ ràng, ngắn gọn và an toàn",
    val welcomeSummary: String = "Chọn một chế độ để quét lối đi, đọc văn bản hoặc chuẩn bị lộ trình trước khi ra ngoài.",
    val actions: List<HomeAction> = defaultHomeActions()
)

private fun defaultHomeActions(): List<HomeAction> = listOf(
    HomeAction(
        type = HomeActionType.ScanAround,
        title = "Xem xung quanh",
        description = "Mở camera để nhận biết vật cản, lối đi và các tín hiệu ngay phía trước.",
        supportingLabel = "Ưu tiên cảnh báo gần",
        accessibilityLabel = "Xem xung quanh. Mở camera để nhận biết vật cản, lối đi và tín hiệu phía trước."
    ),
    HomeAction(
        type = HomeActionType.ReadTextQuick,
        title = "Đọc văn bản nhanh",
        description = "Dùng camera để đọc nhanh bằng ML Kit.",
        supportingLabel = "Chế độ OCR nhanh",
        accessibilityLabel = "Đọc văn bản nhanh. Dùng camera để đọc nhãn, biển báo hoặc tài liệu ngắn bằng OCR nhanh."
    ),
    HomeAction(
        type = HomeActionType.ReadTextAccuracy,
        title = "Đọc văn bản chính xác",
        description = "Dùng camera để đọc chính xác hơn bằng GPT-4o.",
        supportingLabel = "Chế độ OCR chính xác",
        accessibilityLabel = "Đọc văn bản chính xác. Dùng camera để đọc tài liệu với độ chính xác cao hơn bằng GPT-4o."
    ),
    HomeAction(
        type = HomeActionType.Navigate,
        title = "Đi đến nơi",
        description = "Mở bản đồ để xem điểm đến và chuẩn bị cho dẫn đường ở các bước tiếp theo.",
        supportingLabel = "Lộ trình và mốc định hướng",
        accessibilityLabel = "Đi đến nơi. Mở bản đồ để xem điểm đến và chuẩn bị dẫn đường."
    ),
    HomeAction(
        type = HomeActionType.Settings,
        title = "Tinh chỉnh phản hồi",
        description = "Điều chỉnh tốc độ đọc và độ nhạy cảnh báo để phù hợp với môi trường hiện tại.",
        supportingLabel = "Âm thanh và rung",
        accessibilityLabel = "Tinh chỉnh phản hồi. Điều chỉnh tốc độ đọc và độ nhạy cảnh báo."
    )
)

class HomeViewModel(
    private val tts: SpeechOutput
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasSpokenGreeting = false

    fun onScreenShown() {
        if (hasSpokenGreeting) return
        hasSpokenGreeting = true
        tts.speak("Chào mừng. Chọn Xem, Đọc, Đi hoặc Cài đặt để bắt đầu.")
    }
}
