package com.example.eyes.domain.accessibility

import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

enum class AnnouncementCategory {
    Navigation,
    Guidance,
    Status,
    Error,
    Safety,
}

interface AnnouncementController {
    val voiceGuideEnabled: StateFlow<Boolean>

    fun announce(
        text: String,
        category: AnnouncementCategory = AnnouncementCategory.Guidance,
        locale: Locale? = null,
        interruptCurrent: Boolean = false
    ): Boolean

    suspend fun announceAndAwait(
        text: String,
        category: AnnouncementCategory = AnnouncementCategory.Guidance,
        locale: Locale? = null,
    )
}
