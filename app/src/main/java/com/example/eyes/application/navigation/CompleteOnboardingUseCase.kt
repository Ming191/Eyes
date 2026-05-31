package com.example.eyes.application.navigation

import com.example.eyes.application.ports.NavigationPreferencesRepository

class CompleteOnboardingUseCase(
    private val navigationPreferencesRepository: NavigationPreferencesRepository
) {
    suspend operator fun invoke() {
        navigationPreferencesRepository.setOnboardingCompleted(true)
    }
}
