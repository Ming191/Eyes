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
import com.example.eyes.voiceguide.AnnouncementCategory
import com.example.eyes.voiceguide.AnnouncementController
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
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticService,
    private val announcementController: AnnouncementController
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

    fun setVoiceGuideEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setVoiceGuideEnabled(enabled)
            val language = uiState.value.appLanguage
            val text = context.getString(
                if (enabled) R.string.settings_voice_guide_enabled_announcement
                else R.string.settings_voice_guide_disabled_announcement
            )
            announcementController.announce(
                text = text,
                priority = SpeechOutput.Priority.HIGH,
                category = AnnouncementCategory.SystemFeedback,
                locale = language.ttsLocale
            )
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
        announcementController.announce(
            text = text,
            priority = SpeechOutput.Priority.HIGH,
            category = AnnouncementCategory.SystemFeedback,
            locale = state.appLanguage.ttsLocale
        )
        hapticService.confirm()
    }
}
