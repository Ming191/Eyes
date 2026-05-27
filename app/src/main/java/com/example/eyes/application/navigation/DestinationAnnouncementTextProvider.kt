package com.example.eyes.application.navigation

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.navigation.Destination

interface DestinationAnnouncementTextProvider {
    fun intro(destination: Destination, language: AppLanguage): String
}
