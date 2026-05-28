package com.example.eyes.data.settings

import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.settings.SettingsRepository
import com.example.eyes.domain.i18n.AppLanguage

class DataStoreSettingsRepository(
    private val dataStoreManager: DataStoreManager
) : SettingsRepository {
    override val ttsSpeedFlow = dataStoreManager.ttsSpeedFlow
    override val alertSensitivityFlow = dataStoreManager.alertSensitivityFlow
    override val ocrTranslateToVietnameseFlow = dataStoreManager.ocrTranslateToVietnameseFlow
    override val appLanguageFlow = dataStoreManager.appLanguageFlow
    override val voiceGuideEnabledFlow = dataStoreManager.voiceGuideEnabledFlow

    override suspend fun setTtsSpeed(value: Float) {
        dataStoreManager.setTtsSpeed(value)
    }

    override suspend fun setOcrTranslateToVietnamese(enabled: Boolean) {
        dataStoreManager.setOcrTranslateToVietnamese(enabled)
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        dataStoreManager.setAppLanguage(language)
    }
}
