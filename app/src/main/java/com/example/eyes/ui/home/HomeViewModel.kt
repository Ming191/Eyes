package com.example.eyes.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.data.DataStoreManager
import com.example.eyes.system.EmergencyCallService
import com.example.eyes.system.SpeechOutput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class HomeActionType {
    ScanAround,
    ReadTextQuick,
    ReadTextAccuracy,
    Navigate,
    EmergencyCall,
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
        type = HomeActionType.EmergencyCall,
        title = "Gọi khẩn cấp",
        description = "Mở trình gọi tới số khẩn cấp đã lưu để bạn xác nhận cuộc gọi.",
        supportingLabel = "Mặc định 115, có thể đổi trong cài đặt",
        accessibilityLabel = "Gọi khẩn cấp. Mở trình gọi tới số khẩn cấp đã lưu để bạn xác nhận cuộc gọi."
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
    private val tts: SpeechOutput,
    private val dataStoreManager: DataStoreManager,
    private val emergencyCallService: EmergencyCallService
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = dataStoreManager.emergencyPhoneNumberFlow
        .map { HomeUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    val emergencyPhoneNumber: StateFlow<String> = dataStoreManager.emergencyPhoneNumberFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DataStoreManager.DEFAULT_EMERGENCY_PHONE_NUMBER
        )

    private var hasSpokenGreeting = false

    fun onScreenShown() {
        if (hasSpokenGreeting) return
        hasSpokenGreeting = true
        tts.speak("Chào mừng. Chọn Xem, Đọc, Đi hoặc Cài đặt để bắt đầu.")
    }

    fun announceEmergencyConfirmation(phoneNumber: String) {
        tts.speak("Xác nhận gọi khẩn cấp. Ứng dụng sẽ mở trình gọi tới số $phoneNumber.")
    }

    fun openEmergencyDialer(phoneNumber: String) {
        emergencyCallService.openDialer(phoneNumber)
    }
}
