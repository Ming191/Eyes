package com.example.eyes.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.data.DataStoreManager
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.localizedFor
import com.example.eyes.system.SpeechOutput
import com.example.eyes.voiceguide.AnnouncementCategory
import com.example.eyes.voiceguide.AnnouncementController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeActionType {
    ReadTextQuick,
    ReadTextAccuracy,
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
    val welcomeTitle: String = "Hỗ trợ đọc văn bản rõ ràng và ngắn gọn",
    val welcomeSummary: String = "Chọn một chế độ để đọc văn bản, ra lệnh bằng giọng nói hoặc tinh chỉnh phản hồi.",
    val actions: List<HomeAction> = defaultHomeActions()
)

private fun defaultHomeActions(): List<HomeAction> = listOf(
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
        type = HomeActionType.Voice,
        title = "Ra lệnh bằng giọng nói",
        description = "Nói một câu lệnh để mở chế độ đọc, mô tả hoặc nhận diện tiền.",
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
    private val context: Context,
    private val tts: SpeechOutput,
    private val dataStoreManager: DataStoreManager? = null,
    private val announcementController: AnnouncementController? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasSpokenGreeting = false
    private var appLanguage: AppLanguage = AppLanguage.VI
    private var isAppLanguageLoaded = false
    private var pendingGreeting = false

    init {
        dataStoreManager?.let { store ->
            viewModelScope.launch {
                store.appLanguageFlow.collect { language ->
                    appLanguage = language
                    isAppLanguageLoaded = true
                    _uiState.update { language.homeUiState }
                    if (pendingGreeting) {
                        pendingGreeting = false
                        speakGreetingIfNeeded()
                    }
                }
            }
        }
    }

    fun onScreenShown() {
        if (dataStoreManager != null && !isAppLanguageLoaded) {
            pendingGreeting = true
            return
        }
        speakGreetingIfNeeded()
    }

    private fun speakGreetingIfNeeded() {
        if (hasSpokenGreeting) return
        hasSpokenGreeting = true
        val text = context.localizedFor(appLanguage).getString(R.string.home_greeting)
        announcementController?.announce(
            text = text,
            priority = SpeechOutput.Priority.NORMAL,
            category = AnnouncementCategory.Guidance,
            locale = appLanguage.ttsLocale
        ) ?: tts.speak(text, appLanguage.ttsLocale)
    }

    private val AppLanguage.homeUiState: HomeUiState
        get() = context.localizedFor(this).homeUiStateFromResources()

    private fun Context.homeUiStateFromResources(): HomeUiState = HomeUiState(
        welcomeTitle = getString(R.string.home_welcome_title),
        welcomeSummary = getString(R.string.home_welcome_summary),
        actions = listOf(
            HomeAction(
                HomeActionType.ReadTextQuick,
                getString(R.string.home_action_read_quick_title),
                getString(R.string.home_action_read_quick_description),
                getString(R.string.home_action_read_quick_supporting),
                getString(R.string.home_action_read_quick_accessibility)
            ),
            HomeAction(
                HomeActionType.ReadTextAccuracy,
                getString(R.string.home_action_read_accuracy_title),
                getString(R.string.home_action_read_accuracy_description),
                getString(R.string.home_action_read_accuracy_supporting),
                getString(R.string.home_action_read_accuracy_accessibility)
            ),
            HomeAction(
                HomeActionType.Voice,
                getString(R.string.home_action_voice_title),
                getString(R.string.home_action_voice_description),
                getString(R.string.home_action_voice_supporting),
                getString(R.string.home_action_voice_accessibility)
            ),
            HomeAction(
                HomeActionType.Settings,
                getString(R.string.home_action_settings_title),
                getString(R.string.home_action_settings_description),
                getString(R.string.home_action_settings_supporting),
                getString(R.string.home_action_settings_accessibility)
            )
        )
    )
}
