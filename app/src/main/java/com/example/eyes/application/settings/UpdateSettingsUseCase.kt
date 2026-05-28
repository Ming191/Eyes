package com.example.eyes.application.settings

import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.domain.i18n.AppLanguage

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
