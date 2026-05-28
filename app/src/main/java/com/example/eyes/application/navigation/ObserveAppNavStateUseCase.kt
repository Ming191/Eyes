package com.example.eyes.application.navigation

import com.example.eyes.domain.navigation.NavigationPreferencesRepository
import com.example.eyes.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveAppNavStateUseCase(
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<AppNavState> = combine(
        navigationPreferencesRepository.onboardingCompletedFlow,
        settingsRepository.appLanguageFlow
    ) { completed, appLanguage ->
        AppNavState(
            onboardingCompleted = completed,
            appLanguage = appLanguage
        )
    }
}
