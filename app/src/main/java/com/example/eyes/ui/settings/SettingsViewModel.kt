package com.example.eyes.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.data.DataStoreManager
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.localizedFor
import com.example.eyes.system.HapticService
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
    val appLanguage: AppLanguage = AppLanguage.VI
)

class SettingsViewModel(
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticService
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStoreManager.ttsSpeedFlow,
        dataStoreManager.alertSensitivityFlow,
        dataStoreManager.ocrTranslateToVietnameseFlow,
        dataStoreManager.appLanguageFlow
    ) { ttsSpeed, alertSensitivity, autoTranslate, appLanguage ->
        SettingsUiState(
            ttsSpeed = ttsSpeed,
            alertSensitivity = alertSensitivity,
            autoTranslateEnglishOcrToVietnamese = autoTranslate,
            appLanguage = appLanguage
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

    fun setAlertSensitivity(value: Float) {
        viewModelScope.launch {
            dataStoreManager.setAlertSensitivity(value)
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

    fun previewFeedback(state: SettingsUiState) {
        val speedLabel = String.format(state.appLanguage.ttsLocale, "%.2f", state.ttsSpeed)
        val sensitivityLabel = (state.alertSensitivity * 100).toInt()
        speechOutput.setSpeechRate(state.ttsSpeed)
        val text = context.localizedFor(state.appLanguage).getString(
            R.string.settings_preview_feedback_en,
            speedLabel,
            sensitivityLabel
        )
        speechOutput.speak(text, state.appLanguage.ttsLocale)
        hapticService.confirm()
    }
}
