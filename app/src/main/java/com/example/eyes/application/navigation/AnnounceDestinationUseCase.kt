package com.example.eyes.application.navigation

import com.example.eyes.R
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.ui.navigation.TopLevelDestination
import com.example.eyes.voiceguide.AnnouncementCategory
import com.example.eyes.voiceguide.AnnouncementController

class AnnounceDestinationUseCase(
    private val announcementController: AnnouncementController,
    private val localizedTextProvider: LocalizedTextProvider
) {
    operator fun invoke(destination: TopLevelDestination, appLanguage: AppLanguage) {
        val textRes = when (destination) {
            TopLevelDestination.HOME -> R.string.voice_guide_home_intro
            TopLevelDestination.CAMERA -> R.string.voice_guide_camera_intro
            TopLevelDestination.SETTINGS -> R.string.voice_guide_settings_intro
        }
        announcementController.announce(
            text = localizedTextProvider.getString(textRes, appLanguage),
            category = AnnouncementCategory.Navigation,
            locale = appLanguage.ttsLocale,
            interruptCurrent = true
        )
    }
}
