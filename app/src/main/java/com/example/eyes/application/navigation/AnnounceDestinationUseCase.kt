package com.example.eyes.application.navigation

import com.example.eyes.R
import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.domain.accessibility.AnnouncementController
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider

class AnnounceDestinationUseCase(
    private val announcementController: AnnouncementController,
    private val localizedTextProvider: LocalizedTextProvider
) {
    operator fun invoke(destination: Destination, appLanguage: AppLanguage) {
        val textRes = when (destination) {
            Destination.HOME -> R.string.voice_guide_home_intro
            Destination.CAMERA -> R.string.voice_guide_camera_intro
            Destination.SETTINGS -> R.string.voice_guide_settings_intro
        }
        announcementController.announce(
            text = localizedTextProvider.getString(textRes, appLanguage),
            category = AnnouncementCategory.Navigation,
            locale = appLanguage.ttsLocale,
            interruptCurrent = true
        )
    }
}
