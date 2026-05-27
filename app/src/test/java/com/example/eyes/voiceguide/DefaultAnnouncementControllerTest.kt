package com.example.eyes.voiceguide

import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.system.SpeechOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAnnouncementControllerTest {

    private val voiceGuideEnabled = MutableStateFlow(true)
    private val speechOutput = FakeSpeechOutput()
    private val accessibilityStateProvider = FakeAccessibilityStateProvider()
    private var nowMs = 1_000L

    private val controller = DefaultAnnouncementController(
        voiceGuideEnabledFlow = voiceGuideEnabled,
        speechOutput = speechOutput,
        accessibilityStateProvider = accessibilityStateProvider,
        scope = ApplicationScope(Dispatchers.Unconfined),
        nowMs = { nowMs }
    )

    @Test
    fun announce_whenVoiceGuideOff_suppressesGuidanceButAllowsSafety() {
        // GIVEN
        voiceGuideEnabled.value = false

        // WHEN
        controller.announce("Hướng dẫn", category = AnnouncementCategory.Guidance)
        controller.announce("Cẩn thận", category = AnnouncementCategory.Safety)

        // THEN
        assertEquals(listOf("Cẩn thận"), speechOutput.spokenTexts)
    }

    @Test
    fun announce_whenTalkBackLikelyOn_suppressesNavigationButAllowsError() {
        // GIVEN
        accessibilityStateProvider.screenReaderLikelyEnabled = true

        // WHEN
        controller.announce("Trang chủ", category = AnnouncementCategory.Navigation)
        controller.announce("Không thể xử lý", category = AnnouncementCategory.Error)

        // THEN
        assertEquals(listOf("Không thể xử lý"), speechOutput.spokenTexts)
    }

    @Test
    fun announce_dedupesSameTextWithinWindow() {
        // WHEN
        controller.announce("Trang chủ", category = AnnouncementCategory.Guidance)
        nowMs += 500L
        controller.announce("Trang chủ", category = AnnouncementCategory.Guidance)
        nowMs += 3_000L
        controller.announce("Trang chủ", category = AnnouncementCategory.Guidance)

        // THEN
        assertEquals(listOf("Trang chủ", "Trang chủ"), speechOutput.spokenTexts)
    }

    private class FakeSpeechOutput : SpeechOutput {
        val spokenTexts = mutableListOf<String>()

        override fun speak(text: String) {
            spokenTexts.add(text)
        }

        override fun speak(text: String, locale: java.util.Locale) {
            spokenTexts.add(text)
        }
    }

    private class FakeAccessibilityStateProvider : AccessibilityStateProvider {
        var screenReaderLikelyEnabled = false

        override val isTouchExplorationEnabled: Boolean
            get() = screenReaderLikelyEnabled

        override val isScreenReaderLikelyEnabled: Boolean
            get() = screenReaderLikelyEnabled
    }
}
