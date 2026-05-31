package com.example.eyes.application.home

import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.application.ports.AnnouncementPort
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.application.ports.SpeechOutput

class AnnounceHomeGreetingUseCase(
    private val homeAnnouncementTextProvider: HomeAnnouncementTextProvider,
    private val speechOutput: SpeechOutput,
    private val announcementPort: AnnouncementPort? = null
) {
    fun invoke(language: AppLanguage) {
        val text = homeAnnouncementTextProvider.greeting(language)
        announcementPort?.announce(
            text = text,
            category = AnnouncementCategory.Guidance,
            locale = language.ttsLocale
        ) ?: speechOutput.speak(text, language.ttsLocale)
    }
}
