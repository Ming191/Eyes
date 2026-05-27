package com.example.eyes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.data.DataStoreManager
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.system.SpeechOutput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val dataStoreManager: DataStoreManager,
    private val speechOutput: SpeechOutput
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStoreManager.ttsSpeedFlow,
        dataStoreManager.alertSensitivityFlow,
        dataStoreManager.ocrTranslateToVietnameseFlow,
        dataStoreManager.appLanguageFlow,
        dataStoreManager.voiceGuideEnabledFlow
    ) { ttsSpeed, alertSensitivity, autoTranslate, appLanguage, voiceGuideEnabled ->
        SettingsUiState(
            ttsSpeed = ttsSpeed,
            alertSensitivity = alertSensitivity,
            autoTranslateEnglishOcrToVietnamese = autoTranslate,
            appLanguage = appLanguage,
            voiceGuideEnabled = voiceGuideEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setTtsSpeed(value: Float) {
        viewModelScope.launch {
            speechOutput.setSpeechRate(value)
            dataStoreManager.setTtsSpeed(value)
        }
    }

    fun setAutoTranslateEnglishOcrToVietnamese(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setOcrTranslateToVietnamese(enabled)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            dataStoreManager.setAppLanguage(language)
        }
    }

}
