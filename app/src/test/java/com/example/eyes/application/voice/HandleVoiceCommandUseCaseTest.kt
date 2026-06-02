package com.example.eyes.application.voice

import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.application.settings.UpdateSettingsUseCase
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.voice.VoiceCameraTarget
import com.example.eyes.domain.voice.VoiceIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun invoke_captureOcrQuick_returnsCameraActionWithAutoCapture() = runBlocking {
        val action = useCase(
            VoiceIntent.CaptureCamera(VoiceCameraTarget.OCR, OcrMode.QUICK),
            AppLanguage.VI
        )

        assertEquals(VoiceNavigationTargetKind.Camera, action.navigationTarget)
        assertEquals(VoiceCameraTarget.OCR, action.cameraTarget)
        assertEquals(OcrMode.QUICK, action.ocrMode)
        assertEquals(true, action.autoCapture)
        assertEquals(listOf("capture quick"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_openCurrency_returnsCameraActionWithoutAutoCapture() = runBlocking {
        val action = useCase(
            VoiceIntent.OpenCamera(VoiceCameraTarget.CURRENCY),
            AppLanguage.VI
        )

        assertEquals(VoiceNavigationTargetKind.Camera, action.navigationTarget)
        assertEquals(VoiceCameraTarget.CURRENCY, action.cameraTarget)
        assertEquals(false, action.autoCapture)
        assertEquals(listOf("currency"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_dialEmergency_returnsDialNumber() = runBlocking {
        val action = useCase(VoiceIntent.DialEmergency("113"), AppLanguage.VI)

        assertEquals("113", action.dialNumber)
        assertEquals(listOf("dial 113"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_setSpeechSpeed_updatesRepositoryAndSpeaks() = runBlocking {
        useCase(VoiceIntent.SetSpeechSpeed(1.25f), AppLanguage.EN)

        assertEquals(1.25f, settingsRepository.ttsSpeedFlow.value)
        assertEquals(listOf("speed 1.25"), speechOutput.spokenTexts)
    }

    @Test
    fun invoke_relativeSpeechSpeed_usesPresetSteps() = runBlocking {
        settingsRepository.ttsSpeedFlow.value = 1.0f

        useCase(VoiceIntent.IncreaseSpeechSpeed, AppLanguage.EN)
        useCase(VoiceIntent.DecreaseSpeechSpeed, AppLanguage.EN)

        assertEquals(1.0f, settingsRepository.ttsSpeedFlow.value)
    }

    @Test
    fun invoke_setAutoTranslate_updatesRepository() = runBlocking {
        useCase(VoiceIntent.SetAutoTranslate(false), AppLanguage.VI)

        assertEquals(false, settingsRepository.ocrTranslateToVietnameseFlow.value)
        assertEquals(listOf("translate off"), speechOutput.spokenTexts)
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
