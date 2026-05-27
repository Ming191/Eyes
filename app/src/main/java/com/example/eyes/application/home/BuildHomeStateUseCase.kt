package com.example.eyes.application.home

import com.example.eyes.domain.i18n.AppLanguage

class BuildHomeStateUseCase(
    private val homeTextProvider: HomeTextProvider
) {
    operator fun invoke(language: AppLanguage): HomeState =
        homeTextProvider.homeState(language)
}
