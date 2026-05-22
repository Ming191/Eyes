package com.example.eyes.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.data.DataStoreManager
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.system.SpeechOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeActionType {
    ScanAround,
    ReadTextQuick,
    ReadTextAccuracy,
    Navigate,
    Voice,
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
        type = HomeActionType.Voice,
        title = "Ra lệnh bằng giọng nói",
        description = "Nói một câu lệnh để mở chế độ đọc, mô tả, nhận diện tiền hoặc dẫn đường.",
        supportingLabel = "Hỗ trợ tiếng Việt",
        accessibilityLabel = "Ra lệnh bằng giọng nói. Nói một câu lệnh để chọn chế độ phù hợp."
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
    private val dataStoreManager: DataStoreManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasSpokenGreeting = false
    private var appLanguage: AppLanguage = AppLanguage.VI

    init {
        dataStoreManager?.let { store ->
            viewModelScope.launch {
                store.appLanguageFlow.collect { language ->
                    appLanguage = language
                    _uiState.update { language.homeUiState }
                }
            }
        }
    }

    fun onScreenShown() {
        if (hasSpokenGreeting) return
        hasSpokenGreeting = true
        val text = when (appLanguage) {
            AppLanguage.VI -> "Chào mừng. Chọn Xem, Đọc, Đi, Giọng nói hoặc Cài đặt để bắt đầu."
            AppLanguage.EN -> "Welcome. Choose scan, read, navigate, voice, or settings to start."
        }
        tts.speak(text, appLanguage.ttsLocale)
    }

    private val AppLanguage.homeUiState: HomeUiState
        get() = when (this) {
            AppLanguage.VI -> HomeUiState()
            AppLanguage.EN -> HomeUiState(
                welcomeTitle = "Clear, brief, safe mobility support",
                welcomeSummary = "Choose a mode to scan path, read text, or prepare route before going out.",
                actions = listOf(
                    HomeAction(HomeActionType.ScanAround, "Scan around", "Open camera to detect obstacles, paths, and signals ahead.", "Prioritize nearby alerts", "Scan around. Open camera to detect obstacles, paths, and signals ahead."),
                    HomeAction(HomeActionType.ReadTextQuick, "Read text quickly", "Use camera for fast reading with ML Kit.", "Fast OCR mode", "Read text quickly. Use camera to read labels, signs, or short documents with fast OCR."),
                    HomeAction(HomeActionType.ReadTextAccuracy, "Read text accurately", "Use camera for more accurate reading with GPT-4o.", "Accurate OCR mode", "Read text accurately. Use camera to read documents with higher accuracy using GPT-4o."),
                    HomeAction(HomeActionType.Navigate, "Go somewhere", "Open map to view destination and prepare navigation.", "Route and landmarks", "Go somewhere. Open map to view destination and prepare navigation."),
                    HomeAction(HomeActionType.Voice, "Voice command", "Say a command to open reading, description, money recognition, or navigation.", "English supported", "Voice command. Say a command to choose suitable mode."),
                    HomeAction(HomeActionType.Settings, "Adjust feedback", "Adjust speech speed and alert sensitivity for current environment.", "Sound and vibration", "Adjust feedback. Adjust speech speed and alert sensitivity.")
                )
            )
        }
}
