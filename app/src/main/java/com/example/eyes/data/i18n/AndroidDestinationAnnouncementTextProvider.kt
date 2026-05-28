package com.example.eyes.data.i18n

import com.example.eyes.R
import com.example.eyes.application.navigation.DestinationAnnouncementTextProvider
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider

class AndroidDestinationAnnouncementTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) : DestinationAnnouncementTextProvider {
    override fun intro(destination: Destination, language: AppLanguage): String {
        val textRes = when (destination) {
            Destination.HOME -> R.string.voice_guide_home_intro
            Destination.CAMERA -> R.string.voice_guide_camera_intro
            Destination.SETTINGS -> R.string.voice_guide_settings_intro
        }
        return localizedTextProvider.getString(textRes, language)
    }
}
