package com.example.eyes.application.settings

import com.example.eyes.domain.settings.SettingsRepository
import com.example.eyes.domain.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class SettingsState(
    val ttsSpeed: Float = 1.0f,
    val alertSensitivity: Float = 0.5f,
    val autoTranslateEnglishOcrToVietnamese: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.VI,
    val voiceGuideEnabled: Boolean = true
)

class ObserveSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<SettingsState> = combine(
        settingsRepository.ttsSpeedFlow,
        settingsRepository.alertSensitivityFlow,
        settingsRepository.ocrTranslateToVietnameseFlow,
        settingsRepository.appLanguageFlow,
        settingsRepository.voiceGuideEnabledFlow
    ) { ttsSpeed, alertSensitivity, autoTranslate, appLanguage, voiceGuideEnabled ->
        SettingsState(
            ttsSpeed = ttsSpeed,
            alertSensitivity = alertSensitivity,
            autoTranslateEnglishOcrToVietnamese = autoTranslate,
            appLanguage = appLanguage,
            voiceGuideEnabled = voiceGuideEnabled
        )
    }
}
