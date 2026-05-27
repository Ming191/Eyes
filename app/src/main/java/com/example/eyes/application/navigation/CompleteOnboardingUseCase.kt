package com.example.eyes.application.navigation

import com.example.eyes.data.DataStoreManager

class CompleteOnboardingUseCase(
    private val dataStoreManager: DataStoreManager
) {
    suspend operator fun invoke() {
        dataStoreManager.setOnboardingCompleted(true)
    }
}
