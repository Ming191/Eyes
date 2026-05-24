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
    val welcomeTitle: String = "",
    val welcomeSummary: String = "",
    val actions: List<HomeAction> = emptyList()
)

class HomeViewModel(
    private val context: Context,
    private val tts: SpeechOutput,
    private val dataStoreManager: DataStoreManager? = null,
    private val announcementController: AnnouncementController? = null
) : ViewModel() {

    private var hasSpokenGreeting = false
    private var appLanguage: AppLanguage = AppLanguage.VI
    private var isAppLanguageLoaded = false
    private var pendingGreeting = false

    private val _uiState = MutableStateFlow(context.localizedFor(appLanguage).homeUiStateFromResources())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
