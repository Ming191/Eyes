package com.example.eyes.application.home

import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.domain.accessibility.AnnouncementController
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.speech.SpeechOutput

class AnnounceHomeGreetingUseCase(
    private val homeAnnouncementTextProvider: HomeAnnouncementTextProvider,
    private val speechOutput: SpeechOutput,
    private val announcementController: AnnouncementController? = null
) {
    fun invoke(language: AppLanguage) {
        val text = homeAnnouncementTextProvider.greeting(language)
        announcementController?.announce(
            text = text,
            category = AnnouncementCategory.Guidance,
            locale = language.ttsLocale
        ) ?: speechOutput.speak(text, language.ttsLocale)
    }
}
