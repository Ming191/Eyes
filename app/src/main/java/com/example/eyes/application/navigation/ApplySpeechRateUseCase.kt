package com.example.eyes.application.navigation

import com.example.eyes.domain.settings.SettingsRepository
import com.example.eyes.domain.speech.SpeechOutput

class ApplySpeechRateUseCase(
    private val settingsRepository: SettingsRepository,
    private val speechOutput: SpeechOutput
) {
    suspend operator fun invoke() {
        settingsRepository.ttsSpeedFlow.collect { speed ->
            speechOutput.setSpeechRate(speed)
        }
    }
}
