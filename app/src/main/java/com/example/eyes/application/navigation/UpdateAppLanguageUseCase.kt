package com.example.eyes.application.navigation

import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.domain.i18n.AppLanguage

class UpdateAppLanguageUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(language: AppLanguage) {
        settingsRepository.setAppLanguage(language)
    }
}
