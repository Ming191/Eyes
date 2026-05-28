package com.example.eyes.application.navigation

import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.application.ports.AnnouncementPort
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.domain.i18n.AppLanguage

class AnnounceDestinationUseCase(
    private val announcementPort: AnnouncementPort,
    private val destinationAnnouncementTextProvider: DestinationAnnouncementTextProvider
) {
    operator fun invoke(destination: Destination, appLanguage: AppLanguage) {
        announcementPort.announce(
            text = destinationAnnouncementTextProvider.intro(destination, appLanguage),
            category = AnnouncementCategory.Navigation,
            locale = appLanguage.ttsLocale,
            interruptCurrent = true
        )
    }
}
