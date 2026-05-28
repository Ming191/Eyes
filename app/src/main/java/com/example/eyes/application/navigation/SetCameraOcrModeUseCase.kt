package com.example.eyes.application.navigation

import com.example.eyes.application.ports.NavigationPreferencesRepository
import com.example.eyes.domain.ocr.OcrMode

class SetCameraOcrModeUseCase(
    private val navigationPreferencesRepository: NavigationPreferencesRepository
) {
    suspend operator fun invoke(mode: OcrMode) {
        navigationPreferencesRepository.setOcrMode(mode)
    }
}
