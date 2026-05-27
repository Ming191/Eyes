package com.example.eyes.application.home

import com.example.eyes.i18n.AppLanguage

interface HomeTextProvider {
    fun homeState(language: AppLanguage): HomeState
}
