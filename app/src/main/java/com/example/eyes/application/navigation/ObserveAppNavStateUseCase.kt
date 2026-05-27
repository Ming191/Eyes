package com.example.eyes.application.navigation

import com.example.eyes.data.DataStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveAppNavStateUseCase(
    private val dataStoreManager: DataStoreManager
) {
    operator fun invoke(): Flow<AppNavState> = combine(
        dataStoreManager.onboardingCompletedFlow,
        dataStoreManager.appLanguageFlow
    ) { completed, appLanguage ->
        AppNavState(
            onboardingCompleted = completed,
            appLanguage = appLanguage
        )
    }
}
