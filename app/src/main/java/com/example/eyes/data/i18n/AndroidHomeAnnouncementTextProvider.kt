package com.example.eyes.data.i18n

import com.example.eyes.R
import com.example.eyes.application.home.HomeAnnouncementTextProvider
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider

class AndroidHomeAnnouncementTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) : HomeAnnouncementTextProvider {
    override fun greeting(language: AppLanguage): String =
        localizedTextProvider.getString(R.string.home_greeting, language)
}
