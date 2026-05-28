package com.example.eyes.application.home

import com.example.eyes.domain.i18n.AppLanguage

interface HomeAnnouncementTextProvider {
    fun greeting(language: AppLanguage): String
}
