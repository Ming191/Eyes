package com.example.eyes.data.navigation

import com.example.eyes.data.DataStoreManager
import com.example.eyes.application.ports.NavigationPreferencesRepository
import com.example.eyes.domain.ocr.OcrMode

class DataStoreNavigationPreferencesRepository(
    private val dataStoreManager: DataStoreManager
) : NavigationPreferencesRepository {
    override val onboardingCompletedFlow = dataStoreManager.onboardingCompletedFlow
    override val ocrModeFlow = dataStoreManager.ocrModeFlow

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStoreManager.setOnboardingCompleted(completed)
    }

    override suspend fun setOcrMode(mode: OcrMode) {
        dataStoreManager.setOcrMode(mode)
    }
}
