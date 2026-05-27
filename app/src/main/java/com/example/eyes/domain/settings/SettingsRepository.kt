package com.example.eyes.domain.settings

import com.example.eyes.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val ttsSpeedFlow: Flow<Float>
    val alertSensitivityFlow: Flow<Float>
    val ocrTranslateToVietnameseFlow: Flow<Boolean>
    val appLanguageFlow: Flow<AppLanguage>
    val voiceGuideEnabledFlow: Flow<Boolean>

    suspend fun setTtsSpeed(value: Float)
    suspend fun setOcrTranslateToVietnamese(enabled: Boolean)
    suspend fun setAppLanguage(language: AppLanguage)
}
