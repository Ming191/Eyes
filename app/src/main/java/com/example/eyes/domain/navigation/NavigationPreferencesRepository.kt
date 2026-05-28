package com.example.eyes.domain.navigation

import com.example.eyes.domain.ocr.OcrMode
import kotlinx.coroutines.flow.Flow

interface NavigationPreferencesRepository {
    val onboardingCompletedFlow: Flow<Boolean>
    val ocrModeFlow: Flow<OcrMode>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setOcrMode(mode: OcrMode)
}
