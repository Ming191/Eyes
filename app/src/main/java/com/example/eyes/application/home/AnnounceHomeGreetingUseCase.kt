package com.example.eyes.application.home

import com.example.eyes.R
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.system.SpeechOutput
import com.example.eyes.voiceguide.AnnouncementCategory
import com.example.eyes.voiceguide.AnnouncementController

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
