package com.example.eyes.voiceguide

import com.example.eyes.system.SpeechOutput
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

enum class AnnouncementCategory {
    Navigation,
    Guidance,
    Status,
    Error,
    Safety,
    VoiceCommand,
    SystemFeedback
}

interface AnnouncementController {
    val voiceGuideEnabled: StateFlow<Boolean>

    fun announce(
        text: String,
        priority: SpeechOutput.Priority = SpeechOutput.Priority.NORMAL,
        category: AnnouncementCategory = AnnouncementCategory.Guidance,
        locale: Locale? = null,
        interruptCurrent: Boolean = false
    ): Boolean

    suspend fun announceAndAwait(
        text: String,
        priority: SpeechOutput.Priority = SpeechOutput.Priority.NORMAL,
        category: AnnouncementCategory = AnnouncementCategory.Guidance,
        locale: Locale? = null
    )
}

class DefaultAnnouncementController(
    voiceGuideEnabledFlow: Flow<Boolean>,
    private val speechOutput: SpeechOutput,
    private val accessibilityStateProvider: AccessibilityStateProvider,
    scope: ApplicationScope,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : AnnouncementController {
    override val voiceGuideEnabled: StateFlow<Boolean> = voiceGuideEnabledFlow
        .stateIn(scope, SharingStarted.Eagerly, false)

    private var lastText: String? = null
    private var lastSpokenAtMs: Long = 0L

    override fun announce(
        text: String,
        priority: SpeechOutput.Priority,
        category: AnnouncementCategory,
        locale: Locale?,
        interruptCurrent: Boolean
    ): Boolean {
        if (!shouldSpeak(text, category)) return false
        remember(text)
        if (interruptCurrent) {
            speechOutput.stop()
        }
        if (locale == null) {
            speechOutput.speak(text, priority)
        } else {
            speechOutput.speak(text, priority, locale)
        }
        return true
    }

    override suspend fun announceAndAwait(
        text: String,
        priority: SpeechOutput.Priority,
        category: AnnouncementCategory,
        locale: Locale?
    ) {
        if (!shouldSpeak(text, category)) return
        remember(text)
        if (locale == null) {
            speechOutput.speakAndAwait(text, priority)
        } else {
            speechOutput.speakAndAwait(text, priority, locale)
        }
    }

    private fun shouldSpeak(
        text: String,
        category: AnnouncementCategory
    ): Boolean {
        if (text.isBlank()) return false
        if (!voiceGuideEnabled.value && category in voiceGuideOnlyCategories) return false
        if (accessibilityStateProvider.isScreenReaderLikelyEnabled && category in talkBackSuppressedCategories) return false

        val now = nowMs()
        return text != lastText || now - lastSpokenAtMs >= DEDUPE_WINDOW_MS
    }

    private fun remember(text: String) {
        lastText = text
        lastSpokenAtMs = nowMs()
    }

    private companion object {
        const val DEDUPE_WINDOW_MS = 3_000L

        val voiceGuideOnlyCategories = setOf(
            AnnouncementCategory.Navigation,
            AnnouncementCategory.Guidance,
            AnnouncementCategory.Status
        )

        val talkBackSuppressedCategories = setOf(
            AnnouncementCategory.Navigation,
            AnnouncementCategory.Guidance,
            AnnouncementCategory.Status
        )
    }
}
