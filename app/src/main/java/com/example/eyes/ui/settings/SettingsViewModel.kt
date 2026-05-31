package com.example.eyes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.application.settings.ObserveSettingsUseCase
import com.example.eyes.application.settings.UpdateSettingsUseCase
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val ttsSpeed: Float = 1.0f,
    val alertSensitivity: Float = 0.5f,
    val autoTranslateEnglishOcrToVietnamese: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.VI,
    val voiceGuideEnabled: Boolean = true
)

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val speechOutput: SpeechOutput,
    private val localizedTextProvider: LocalizedTextProvider
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = observeSettings().map { settings ->
        SettingsUiState(
            ttsSpeed = settings.ttsSpeed,
            alertSensitivity = settings.alertSensitivity,
            autoTranslateEnglishOcrToVietnamese = settings.autoTranslateEnglishOcrToVietnamese,
            appLanguage = settings.appLanguage,
            voiceGuideEnabled = settings.voiceGuideEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setTtsSpeed(value: Float) {
        viewModelScope.launch {
            updateSettings.setTtsSpeed(value)
        }
    }

    fun setAutoTranslateEnglishOcrToVietnamese(enabled: Boolean) {
        viewModelScope.launch {
            updateSettings.setAutoTranslateEnglishOcrToVietnamese(enabled)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            updateSettings.setAppLanguage(language)
            val announcement = localizedTextProvider.getString(
                resId = when (language) {
                    AppLanguage.VI -> R.string.settings_language_changed_vietnamese
                    AppLanguage.EN -> R.string.settings_language_changed_english
                },
                language = language
            )
            speechOutput.speak(announcement, language.ttsLocale)
        }
    }

}
