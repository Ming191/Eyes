package com.example.eyes.application.ports

import com.example.eyes.domain.accessibility.AnnouncementCategory
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

interface AnnouncementPort {
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
