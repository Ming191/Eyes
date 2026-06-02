package com.example.eyes.data.i18n

import androidx.test.core.app.ApplicationProvider
import com.example.eyes.R
import com.example.eyes.application.home.HomeActionKind
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.infrastructure.i18n.AndroidLocalizedTextProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidTextProvidersTest {

    private val localizedTextProvider = AndroidLocalizedTextProvider(ApplicationProvider.getApplicationContext())

    @Test
    fun homeTextProviderBuildsLocalizedHomeState() {
        val state = AndroidHomeTextProvider(localizedTextProvider).homeState(AppLanguage.EN)

        assertEquals(text(R.string.home_welcome_title), state.welcomeTitle)
        assertEquals(text(R.string.home_welcome_summary), state.welcomeSummary)
        assertEquals(6, state.actions.size)
        assertEquals(HomeActionKind.ReadTextQuick, state.actions[0].kind)
        assertEquals(text(R.string.home_action_read_quick_title), state.actions[0].title)
        assertEquals(HomeActionKind.ReadTextAccuracy, state.actions[1].kind)
        assertEquals(HomeActionKind.RecognizeCurrency, state.actions[4].kind)
        assertEquals(HomeActionKind.EmergencyCall, state.actions.last().kind)
        assertEquals(text(R.string.home_action_emergency_accessibility), state.actions.last().accessibilityLabel)
    }

    @Test
    fun announcementProvidersReturnLocalizedStrings() {
        assertEquals(
            text(R.string.home_greeting),
            AndroidHomeAnnouncementTextProvider(localizedTextProvider).greeting(AppLanguage.EN)
        )
        assertEquals(
            text(R.string.voice_guide_home_intro),
            AndroidDestinationAnnouncementTextProvider(localizedTextProvider).intro(Destination.HOME, AppLanguage.EN)
        )
        assertEquals(
            text(R.string.voice_guide_camera_intro),
            AndroidDestinationAnnouncementTextProvider(localizedTextProvider).intro(Destination.CAMERA, AppLanguage.EN)
        )
        assertEquals(
            text(R.string.voice_guide_settings_intro),
            AndroidDestinationAnnouncementTextProvider(localizedTextProvider).intro(Destination.SETTINGS, AppLanguage.EN)
        )
    }

    @Test
    fun voiceCommandTextProviderMapsAllAcknowledgements() {
        val commandText = AndroidVoiceCommandTextProvider(localizedTextProvider).text(AppLanguage.EN)

        assertEquals(text(R.string.voice_vm_read_text_ack), commandText.readText)
        assertEquals(text(R.string.voice_vm_describe_scene_ack), commandText.describeScene)
        assertEquals(text(R.string.voice_vm_recognize_currency_ack), commandText.recognizeCurrency)
        assertEquals(text(R.string.voice_vm_detect_objects_ack), commandText.detectObjects)
        assertEquals(text(R.string.voice_vm_open_home_ack), commandText.openHome)
        assertEquals(text(R.string.voice_vm_open_settings_ack), commandText.openSettings)
        assertEquals(text(R.string.voice_vm_open_emergency_ack), commandText.openEmergency)
        assertEquals(text(R.string.voice_vm_ocr_quick_ack), commandText.ocrQuick)
        assertEquals(text(R.string.voice_vm_ocr_accurate_ack), commandText.ocrAccurate)
        assertEquals(text(R.string.voice_vm_stopped_ack), commandText.stop)
        assertEquals(text(R.string.voice_vm_nothing_to_repeat), commandText.nothingToRepeat)
        assertEquals(text(R.string.voice_vm_help_text), commandText.help)
        assertEquals(text(R.string.voice_vm_unknown_command), commandText.unknown)
        assertTrue(AndroidVoiceCommandTextProvider(localizedTextProvider).text(AppLanguage.VI).help.isNotBlank())
    }

    private fun text(resId: Int): String = localizedTextProvider.getString(resId, AppLanguage.EN)
}
