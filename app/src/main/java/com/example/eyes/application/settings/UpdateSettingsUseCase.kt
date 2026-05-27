package com.example.eyes.application.settings

import com.example.eyes.domain.settings.SettingsRepository
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.system.SpeechOutput

class UpdateSettingsUseCase(
    private val settingsRepository: SettingsRepository,
    private val speechOutput: SpeechOutput
) {
    suspend fun setTtsSpeed(value: Float) {
        speechOutput.setSpeechRate(value)
        settingsRepository.setTtsSpeed(value)
    }

    suspend fun setAutoTranslateEnglishOcrToVietnamese(enabled: Boolean) {
        settingsRepository.setOcrTranslateToVietnamese(enabled)
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        settingsRepository.setAppLanguage(language)
    }
}
