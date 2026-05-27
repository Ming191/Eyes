package com.example.eyes.application.navigation

import com.example.eyes.domain.navigation.NavigationPreferencesRepository

class CompleteOnboardingUseCase(
    private val navigationPreferencesRepository: NavigationPreferencesRepository
) {
    suspend operator fun invoke() {
        navigationPreferencesRepository.setOnboardingCompleted(true)
    }
}
