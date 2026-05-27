package com.example.eyes.application.navigation

import com.example.eyes.domain.settings.SettingsRepository
import com.example.eyes.i18n.AppLanguage

class UpdateAppLanguageUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(language: AppLanguage) {
        settingsRepository.setAppLanguage(language)
    }
}
