package com.example.eyes.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.data.DataStoreManager
import com.example.eyes.i18n.AndroidLocalizedTextProvider
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
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
    DescribeScene,
    DetectObjects,
    RecognizeCurrency,
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
    val welcomeTitle: String = "",
    val welcomeSummary: String = "",
    val actions: List<HomeAction> = emptyList()
)

class HomeViewModel(
    private val localizedTextProvider: LocalizedTextProvider,
    private val tts: SpeechOutput,
    private val dataStoreManager: DataStoreManager? = null,
    private val announcementController: AnnouncementController? = null
) : ViewModel() {

    constructor(
        context: Context,
        tts: SpeechOutput,
        dataStoreManager: DataStoreManager? = null,
        announcementController: AnnouncementController? = null
    ) : this(
        localizedTextProvider = AndroidLocalizedTextProvider(context),
        tts = tts,
        dataStoreManager = dataStoreManager,
        announcementController = announcementController
    )

    private val _uiState = MutableStateFlow(homeUiStateFor(AppLanguage.VI))
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
                    _uiState.update { homeUiStateFor(language) }
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
        val text = localizedTextProvider.getString(R.string.home_greeting, appLanguage)
        announcementController?.announce(
            text = text,
            priority = SpeechOutput.Priority.NORMAL,
            category = AnnouncementCategory.Guidance,
            locale = appLanguage.ttsLocale
        ) ?: tts.speak(text, appLanguage.ttsLocale)
    }

    private fun homeUiStateFor(language: AppLanguage): HomeUiState =
        localizedTextProvider.localizedContext(language).homeUiStateFromResources()

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
                HomeActionType.DescribeScene,
                getString(R.string.camera_mode_scene_description_label),
                getString(R.string.camera_mode_scene_description_description),
                getString(R.string.camera_mode_scene_description_label),
                getString(R.string.camera_mode_scene_description_description)
            ),
            HomeAction(
                HomeActionType.DetectObjects,
                getString(R.string.camera_mode_object_detection_label),
                getString(R.string.camera_mode_object_detection_description),
                getString(R.string.camera_mode_object_detection_label),
                getString(R.string.camera_mode_object_detection_description)
            ),
            HomeAction(
                HomeActionType.RecognizeCurrency,
                getString(R.string.camera_mode_currency_label),
                getString(R.string.camera_mode_currency_description),
                getString(R.string.camera_mode_currency_label),
                getString(R.string.camera_mode_currency_description)
            ),
            HomeAction(
                HomeActionType.Voice,
                getString(R.string.home_action_voice_title),
                getString(R.string.home_action_voice_description),
                getString(R.string.home_action_voice_supporting),
                getString(R.string.home_action_voice_accessibility)
            ),
        )
    )
}
