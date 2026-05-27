package com.example.eyes.application.home

import com.example.eyes.R
import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.domain.accessibility.AnnouncementController
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.domain.speech.SpeechOutput

class AnnounceHomeGreetingUseCase(
    private val localizedTextProvider: LocalizedTextProvider,
    private val speechOutput: SpeechOutput,
    private val announcementController: AnnouncementController? = null
) {
    fun invoke(language: AppLanguage) {
        val text = localizedTextProvider.getString(R.string.home_greeting, language)
        announcementController?.announce(
            text = text,
            category = AnnouncementCategory.Guidance,
            locale = language.ttsLocale
        ) ?: speechOutput.speak(text, language.ttsLocale)
    }
}
