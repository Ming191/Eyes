package com.example.eyes.application.voice

import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.application.settings.UpdateSettingsUseCase
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.voice.VoiceCameraTarget
import com.example.eyes.domain.voice.VoiceIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleVoiceCommandUseCaseTest {

    private val settingsRepository = FakeSettingsRepository()
    private val speechOutput = FakeSpeechOutput()
    private val textProvider = FakeVoiceCommandTextProvider()
    private val useCase = HandleVoiceCommandUseCase(
        speechOutput = speechOutput,
        voiceCommandTextProvider = textProvider,
        updateSettings = UpdateSettingsUseCase(settingsRepository, speechOutput),
        settingsRepository = settingsRepository
    )

    @Test
    fun invoke_captureOcrQuick_returnsCameraActionWithAutoCapture() = runTest {
        val action = useCase(
            VoiceIntent.CaptureCamera(VoiceCameraTarget.OCR, OcrMode.QUICK),
            AppLanguage.VI
        )

        assertEquals(VoiceNavigationTargetKind.Camera, action.navigationTarget)
        assertEquals(VoiceCameraTarget.OCR, action.cameraTarget)
        assertEquals(OcrMode.QUICK, action.ocrMode)
        assertTrue(action.autoCapture)
        assertEquals(listOf("capture quick"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_openCameraIntents_returnCameraActionWithoutAutoCapture() = runTest {
        val cases = listOf(
            VoiceIntent.OpenCamera(VoiceCameraTarget.CURRENCY) to ("currency" to VoiceCameraTarget.CURRENCY),
            VoiceIntent.OpenCamera(VoiceCameraTarget.SCENE_DESCRIPTION) to ("scene" to VoiceCameraTarget.SCENE_DESCRIPTION),
            VoiceIntent.OpenCamera(VoiceCameraTarget.OBJECT_DETECTION) to ("objects" to VoiceCameraTarget.OBJECT_DETECTION),
            VoiceIntent.OpenCamera(VoiceCameraTarget.OCR, OcrMode.ACCURACY) to ("accurate" to VoiceCameraTarget.OCR)
        )

        cases.forEach { (intent, expected) ->
            val action = useCase(intent, AppLanguage.VI)

            assertEquals(VoiceNavigationTargetKind.Camera, action.navigationTarget)
            assertEquals(expected.second, action.cameraTarget)
            assertFalse(action.autoCapture)
            assertEquals(expected.first, speechOutput.spokenTexts.last())
        }
    }

    @Test
    fun invoke_navigationIntents_returnExpectedTargets() = runTest {
        val cases = listOf(
            VoiceIntent.OpenHome to ("home" to VoiceNavigationTargetKind.Home),
            VoiceIntent.OpenSettings to ("settings" to VoiceNavigationTargetKind.Settings),
            VoiceIntent.OpenEmergencyList to ("emergency" to VoiceNavigationTargetKind.Emergency)
        )

        cases.forEach { (intent, expected) ->
            val action = useCase(intent, AppLanguage.EN)

            assertEquals(expected.second, action.navigationTarget)
            assertEquals(expected.first, speechOutput.spokenTexts.last())
            assertFalse(action.shouldRestartListening)
        }
    }

    @Test
    fun invoke_dialEmergency_returnsDialNumber() = runTest {
        val action = useCase(VoiceIntent.DialEmergency("113"), AppLanguage.VI)

        assertEquals("113", action.dialNumber)
        assertEquals(listOf("dial 113"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_setSpeechSpeed_updatesRepositoryAndSpeaks() = runTest {
        useCase(VoiceIntent.SetSpeechSpeed(1.25f), AppLanguage.EN)

        assertEquals(1.25f, settingsRepository.ttsSpeedFlow.value)
        assertEquals(listOf("speed 1.25"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_relativeSpeechSpeed_usesPresetSteps() = runTest {
        settingsRepository.ttsSpeedFlow.value = 1.0f

        useCase(VoiceIntent.IncreaseSpeechSpeed, AppLanguage.EN)
        useCase(VoiceIntent.DecreaseSpeechSpeed, AppLanguage.EN)

        assertEquals(1.0f, settingsRepository.ttsSpeedFlow.value)
    }

    @Test
    fun invoke_setAppLanguage_updatesRepositoryAndSpeaksInNewLanguage() = runTest {
        useCase(VoiceIntent.SetAppLanguage(AppLanguage.EN), AppLanguage.VI)

        assertEquals(AppLanguage.EN, settingsRepository.appLanguageFlow.value)
        assertEquals(listOf("language en"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_setAutoTranslate_updatesRepository() = runTest {
        useCase(VoiceIntent.SetAutoTranslate(false), AppLanguage.VI)

        assertFalse(settingsRepository.ocrTranslateToVietnameseFlow.value)
        assertEquals(listOf("translate off"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_repeatWithoutPriorSpeech_speaksNothingToRepeatAndRestartsListening() = runTest {
        val action = useCase(VoiceIntent.Repeat, AppLanguage.EN)

        assertEquals("nothing", speechOutput.spokenTexts.last())
        assertTrue(action.shouldRestartListening)
        assertNull(action.navigationTarget)
    }

    @Test
    fun invoke_repeatAfterPriorSpeech_repeatsLastSpokenText() = runTest {
        useCase(VoiceIntent.OpenSettings, AppLanguage.EN)

        val action = useCase(VoiceIntent.Repeat, AppLanguage.EN)

        assertEquals(listOf("settings", "settings"), speechOutput.spokenTexts)
        assertTrue(action.shouldRestartListening)
    }

    @Test
    fun invoke_helpAndUnknown_restartListening() = runTest {
        val help = useCase(VoiceIntent.Help, AppLanguage.EN)
        val unknown = useCase(VoiceIntent.Unknown("wat"), AppLanguage.EN)

        assertEquals("unknown", speechOutput.spokenTexts.last())
        assertTrue(help.shouldRestartListening)
        assertTrue(help.shouldExpandHelp)
        assertTrue(unknown.shouldRestartListening)
    }

    @Test
    fun invoke_stop_speaksStopAndReturnsHomeTarget() = runTest {
        val action = useCase(VoiceIntent.Stop, AppLanguage.EN)

        assertEquals("stop", speechOutput.spokenTexts.last())
        assertEquals(VoiceNavigationTargetKind.Home, action.navigationTarget)
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val ttsSpeedFlow = MutableStateFlow(1.0f)
        override val alertSensitivityFlow = MutableStateFlow(0.5f)
        override val ocrTranslateToVietnameseFlow = MutableStateFlow(true)
        override val appLanguageFlow = MutableStateFlow(AppLanguage.VI)
        override val voiceGuideEnabledFlow = MutableStateFlow(true)

        override suspend fun setTtsSpeed(value: Float) {
            ttsSpeedFlow.value = value
        }

        override suspend fun setOcrTranslateToVietnamese(enabled: Boolean) {
            ocrTranslateToVietnameseFlow.value = enabled
        }

        override suspend fun setAppLanguage(language: AppLanguage) {
            appLanguageFlow.value = language
        }
    }

    private class FakeSpeechOutput : SpeechOutput {
        val spokenTexts = mutableListOf<String>()

        override fun speak(text: String) {
            spokenTexts += text
        }
    }

    private class FakeVoiceCommandTextProvider : VoiceCommandTextProvider {
        override fun text(language: AppLanguage): VoiceCommandText = VoiceCommandText(
            readText = "read",
            describeScene = "scene",
            recognizeCurrency = "currency",
            detectObjects = "objects",
            openHome = "home",
            openSettings = "settings",
            openEmergency = "emergency",
            ocrQuick = "quick",
            ocrAccurate = "accurate",
            captureOcrQuick = "capture quick",
            captureOcrAccurate = "capture accurate",
            captureScene = "capture scene",
            captureCurrency = "capture currency",
            autoTranslateEnabled = "translate on",
            autoTranslateDisabled = "translate off",
            stop = "stop",
            nothingToRepeat = "nothing",
            help = "help",
            unknown = "unknown"
        )

        override fun ttsSpeedChanged(language: AppLanguage, speedLabel: String): String = "speed $speedLabel"

        override fun appLanguageChanged(language: AppLanguage): String = "language ${language.storageValue}"

        override fun dialEmergency(language: AppLanguage, number: String): String = "dial $number"
    }
}
