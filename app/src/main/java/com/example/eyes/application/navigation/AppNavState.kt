package com.example.eyes.application.navigation

import com.example.eyes.domain.i18n.AppLanguage

data class AppNavState(
    val onboardingCompleted: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.VI
)
