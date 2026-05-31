package com.example.eyes.application.camera

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.application.ports.NavigationPreferencesRepository
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.application.ports.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class CameraPreferences(
    val appLanguage: AppLanguage = AppLanguage.VI,
    val ocrMode: OcrMode = OcrMode.QUICK,
    val ocrTranslateToVietnamese: Boolean = false
)

class ObserveCameraPreferencesUseCase(
    private val settingsRepository: SettingsRepository,
    private val navigationPreferencesRepository: NavigationPreferencesRepository
) {
    operator fun invoke(): Flow<CameraPreferences> = combine(
        settingsRepository.appLanguageFlow,
        navigationPreferencesRepository.ocrModeFlow,
        settingsRepository.ocrTranslateToVietnameseFlow
    ) { appLanguage, ocrMode, ocrTranslateToVietnamese ->
        CameraPreferences(
            appLanguage = appLanguage,
            ocrMode = ocrMode,
            ocrTranslateToVietnamese = ocrTranslateToVietnamese
        )
    }
}
