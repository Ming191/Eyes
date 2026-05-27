package com.example.eyes.application.navigation

import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.domain.accessibility.AnnouncementController
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.domain.i18n.AppLanguage

class AnnounceDestinationUseCase(
    private val announcementController: AnnouncementController,
    private val destinationAnnouncementTextProvider: DestinationAnnouncementTextProvider
) {
    operator fun invoke(destination: Destination, appLanguage: AppLanguage) {
        announcementController.announce(
            text = destinationAnnouncementTextProvider.intro(destination, appLanguage),
            category = AnnouncementCategory.Navigation,
            locale = appLanguage.ttsLocale,
            interruptCurrent = true
        )
    }
}
