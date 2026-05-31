package com.example.eyes.application.navigation

import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.application.ports.SpeechOutput

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
