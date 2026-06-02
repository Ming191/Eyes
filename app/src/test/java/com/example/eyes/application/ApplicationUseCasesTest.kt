package com.example.eyes.application

import com.example.eyes.application.camera.ObserveCameraPreferencesUseCase
import com.example.eyes.application.currency.RecognizeCurrencyUseCase
import com.example.eyes.application.home.AnnounceHomeGreetingUseCase
import com.example.eyes.application.home.BuildHomeStateUseCase
import com.example.eyes.application.home.HomeActionKind
import com.example.eyes.application.home.HomeActionState
import com.example.eyes.application.home.HomeAnnouncementTextProvider
import com.example.eyes.application.home.HomeState
import com.example.eyes.application.home.HomeTextProvider
import com.example.eyes.application.navigation.AnnounceDestinationUseCase
import com.example.eyes.application.navigation.ApplySpeechRateUseCase
import com.example.eyes.application.navigation.DestinationAnnouncementTextProvider
import com.example.eyes.application.navigation.ObserveAppNavStateUseCase
import com.example.eyes.application.objectdetection.DetectObjectsUseCase
import com.example.eyes.application.objectdetection.WarmUpObjectDetectionUseCase
import com.example.eyes.application.ports.AnnouncementPort
import com.example.eyes.application.ports.CurrencyRecognizerFactory
import com.example.eyes.application.ports.CurrencyRecognizerPort
import com.example.eyes.application.ports.NavigationPreferencesRepository
import com.example.eyes.application.ports.ObjectDetectorPort
import com.example.eyes.application.ports.SceneDescriptionRepository
import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.application.scene.DescribeSceneUseCase
import com.example.eyes.application.settings.ObserveSettingsUseCase
import com.example.eyes.application.settings.SettingsState
import com.example.eyes.application.settings.UpdateSettingsUseCase
import com.example.eyes.application.voice.NoOpSemanticVoiceCommandMatcher
import com.example.eyes.domain.accessibility.AnnouncementCategory
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.image.ImageFormat
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.domain.objectdetection.Detection
import com.example.eyes.domain.objectdetection.DetectionBounds
import com.example.eyes.domain.objectdetection.DetectionPosition
import com.example.eyes.domain.objectdetection.YoloOutputInfo
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.scene.SceneDescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class ApplicationUseCasesTest {
    private val frame = ImageFrame(byteArrayOf(1, 2), 1, 2, ImageFormat.JPEG)

    @Test
    fun updateSettingsDelegatesToRepositoryAndSpeechOutput() = runTest {
        val settings = FakeSettingsRepository()
        val speech = FakeSpeechOutput()
        val useCase = UpdateSettingsUseCase(settings, speech)

        useCase.setTtsSpeed(1.4f)
        useCase.setAutoTranslateEnglishOcrToVietnamese(true)
        useCase.setAppLanguage(AppLanguage.EN)

        assertEquals(1.4f, settings.ttsSpeedFlow.value)
        assertEquals(1.4f, speech.rate)
        assertEquals(true, settings.ocrTranslateToVietnameseFlow.value)
        assertEquals(AppLanguage.EN, settings.appLanguageFlow.value)
    }

    @Test
    fun observeUseCasesCombineLatestPreferences() = runTest {
        val settings = FakeSettingsRepository()
        val nav = FakeNavigationPreferencesRepository()
        settings.setAppLanguage(AppLanguage.EN)
        settings.setOcrTranslateToVietnamese(true)
        nav.setOnboardingCompleted(true)
        nav.setOcrMode(OcrMode.ACCURACY)

        val appNavState = ObserveAppNavStateUseCase(nav, settings).invoke().first()
        val cameraPreferences = ObserveCameraPreferencesUseCase(settings, nav).invoke().first()

        assertEquals(true, appNavState.onboardingCompleted)
        assertEquals(AppLanguage.EN, appNavState.appLanguage)
        assertEquals(AppLanguage.EN, cameraPreferences.appLanguage)
        assertEquals(OcrMode.ACCURACY, cameraPreferences.ocrMode)
        assertEquals(true, cameraPreferences.ocrTranslateToVietnamese)
    }

    @Test
    fun observeSettingsUseCaseCombinesRepositoryFlows() = runTest {
        val settings = FakeSettingsRepository()
        settings.ttsSpeedFlow.value = 1.25f
        settings.alertSensitivityFlow.value = 0.75f
        settings.setOcrTranslateToVietnamese(true)
        settings.setAppLanguage(AppLanguage.EN)
        settings.voiceGuideEnabledFlow.value = false

        val state = ObserveSettingsUseCase(settings).invoke().first()

        assertEquals(
            SettingsState(
                ttsSpeed = 1.25f,
                alertSensitivity = 0.75f,
                autoTranslateEnglishOcrToVietnamese = true,
                appLanguage = AppLanguage.EN,
                voiceGuideEnabled = false
            ),
            state
        )
    }

    @Test
    fun settingsStateDefaultsMatchVietnameseVoiceGuideOn() {
        val state = SettingsState()

        assertEquals(1.0f, state.ttsSpeed)
        assertEquals(0.5f, state.alertSensitivity)
        assertEquals(false, state.autoTranslateEnglishOcrToVietnamese)
        assertEquals(AppLanguage.VI, state.appLanguage)
        assertEquals(true, state.voiceGuideEnabled)
    }

    @Test
    fun applySpeechRateUseCaseCollectsUpdatesUntilCancelled() = runTest {
        val settings = FakeSettingsRepository()
        val speech = FakeSpeechOutput()

        val job = launch { ApplySpeechRateUseCase(settings, speech).invoke() }
        settings.ttsSpeedFlow.value = 1.6f
        advanceUntilIdle()

        assertEquals(1.6f, speech.rate)
        job.cancel()
    }

    @Test
    fun noOpSemanticVoiceCommandMatcherReturnsNull() {
        val matcher = NoOpSemanticVoiceCommandMatcher()

        assertNull(matcher.match("mở camera", AppLanguage.VI))
        assertNull(matcher.match("open camera", AppLanguage.EN))
    }

    @Test
    fun speechOutputDefaultMethodsDelegateOrNoOp() = runTest {
        val speech = MinimalSpeechOutput()

        speech.speak("hello", Locale.US)
        speech.speakAndAwait("await")
        speech.speakAndAwait("await-locale", Locale.CANADA)
        speech.setSpeechRate(2f)
        speech.warmupLocale(Locale.FRANCE)
        speech.stop()

        assertEquals(listOf("hello", "await", "await-locale"), speech.spoken)
        assertNull(speech.currentSpokenText.firstOrNull())
    }

    @Test
    fun homeUseCasesBuildStateAndAnnounceViaAvailablePort() {
        val homeState = HomeState(
            welcomeTitle = "hello",
            welcomeSummary = "summary",
            actions = listOf(HomeActionState(HomeActionKind.DescribeScene, "title", "description", "support", "access"))
        )
        val textProvider = object : HomeTextProvider {
            override fun homeState(language: AppLanguage) = homeState
        }
        assertSame(homeState, BuildHomeStateUseCase(textProvider).invoke(AppLanguage.VI))

        val announcement = FakeAnnouncementPort()
        val speech = FakeSpeechOutput()
        val greetingProvider = object : HomeAnnouncementTextProvider {
            override fun greeting(language: AppLanguage) = "xin chào"
        }

        AnnounceHomeGreetingUseCase(greetingProvider, speech, announcement).invoke(AppLanguage.VI)
        assertEquals("xin chào", announcement.lastText)
        assertEquals(AnnouncementCategory.Guidance, announcement.lastCategory)
        assertNull(speech.lastText)

        AnnounceHomeGreetingUseCase(greetingProvider, speech).invoke(AppLanguage.EN)
        assertEquals("xin chào", speech.lastText)
        assertEquals(AppLanguage.EN.ttsLocale, speech.lastLocale)
    }

    @Test
    fun announceDestinationUsesLocalizedTextAndNavigationCategory() {
        val announcement = FakeAnnouncementPort()
        val provider = object : DestinationAnnouncementTextProvider {
            override fun intro(destination: Destination, language: AppLanguage) = "go ${destination.name}"
        }

        AnnounceDestinationUseCase(announcement, provider).invoke(Destination.SETTINGS, AppLanguage.EN)

        assertEquals("go SETTINGS", announcement.lastText)
        assertEquals(AnnouncementCategory.Navigation, announcement.lastCategory)
        assertEquals(AppLanguage.EN.ttsLocale, announcement.lastLocale)
        assertEquals(true, announcement.lastInterruptCurrent)
    }

    @Test
    fun detectorUseCasesDelegateToDetector() = runTest {
        val detection = Detection(1, "cup", 0.9f, DetectionBounds(0f, 0f, 1f, 1f), DetectionPosition.CENTER)
        val outputInfo = listOf(YoloOutputInfo(index = 0, shape = listOf(1L, 2L, 3L), dtype = "float32", elementCount = 6L))
        val detector = FakeObjectDetector(listOf(detection), outputInfo)

        assertEquals(listOf(detection), DetectObjectsUseCase(detector).invoke(frame))
        assertEquals(outputInfo, WarmUpObjectDetectionUseCase(detector).invoke())
        assertSame(frame, detector.lastFrame)
    }

    @Test
    fun sceneUseCaseDelegatesToRepository() = runTest {
        val expected = SceneDescription.Success("caption")
        val repository = object : SceneDescriptionRepository {
            override suspend fun describeScene(imageFrame: ImageFrame, language: AppLanguage): SceneDescription {
                assertSame(frame, imageFrame)
                assertEquals(AppLanguage.VI, language)
                return expected
            }
        }

        assertSame(expected, DescribeSceneUseCase(repository).invoke(frame, AppLanguage.VI))
    }

    @Test
    fun currencyUseCaseReusesRecognizerForSameCallbackAndClosesOldOne() {
        val factory = FakeCurrencyRecognizerFactory()
        val useCase = RecognizeCurrencyUseCase(factory)
        val callback: (String, Float) -> Unit = { _, _ -> }

        useCase.prepare(callback)
        useCase.analyze(frame, callback)
        useCase.resetBuffer()
        assertEquals(1, factory.created.size)
        assertSame(frame, factory.created.single().lastFrame)
        assertEquals(1, factory.created.single().resetCount)

        useCase.prepare { _, _ -> }
        assertEquals(2, factory.created.size)
        assertEquals(true, factory.created.first().closed)

        useCase.close()
        assertEquals(true, factory.created.last().closed)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    override val ttsSpeedFlow = MutableStateFlow(1f)
    override val alertSensitivityFlow = MutableStateFlow(0.5f)
    override val ocrTranslateToVietnameseFlow = MutableStateFlow(false)
    override val appLanguageFlow = MutableStateFlow(AppLanguage.VI)
    override val voiceGuideEnabledFlow = MutableStateFlow(true)
    override suspend fun setTtsSpeed(value: Float) { ttsSpeedFlow.value = value }
    override suspend fun setOcrTranslateToVietnamese(enabled: Boolean) { ocrTranslateToVietnameseFlow.value = enabled }
    override suspend fun setAppLanguage(language: AppLanguage) { appLanguageFlow.value = language }
}

private class FakeNavigationPreferencesRepository : NavigationPreferencesRepository {
    override val onboardingCompletedFlow = MutableStateFlow(false)
    override val ocrModeFlow = MutableStateFlow(OcrMode.QUICK)
    override suspend fun setOnboardingCompleted(completed: Boolean) { onboardingCompletedFlow.value = completed }
    override suspend fun setOcrMode(mode: OcrMode) { ocrModeFlow.value = mode }
}

private class FakeSpeechOutput : SpeechOutput {
    var lastText: String? = null
    var lastLocale: Locale? = null
    var rate: Float? = null
    override fun speak(text: String) { lastText = text }
    override fun speak(text: String, locale: Locale) { lastText = text; lastLocale = locale }
    override fun setSpeechRate(rate: Float) { this.rate = rate }
}

private class MinimalSpeechOutput : SpeechOutput {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) { spoken += text }
}

private class FakeAnnouncementPort : AnnouncementPort {
    override val voiceGuideEnabled: StateFlow<Boolean> = MutableStateFlow(true)
    var lastText: String? = null
    var lastCategory: AnnouncementCategory? = null
    var lastLocale: Locale? = null
    var lastInterruptCurrent: Boolean? = null
    override fun announce(text: String, category: AnnouncementCategory, locale: Locale?, interruptCurrent: Boolean): Boolean {
        lastText = text; lastCategory = category; lastLocale = locale; lastInterruptCurrent = interruptCurrent
        return true
    }
    override suspend fun announceAndAwait(text: String, category: AnnouncementCategory, locale: Locale?) = Unit
}

private class FakeObjectDetector(
    private val detections: List<Detection>,
    private val outputInfo: List<YoloOutputInfo>
) : ObjectDetectorPort {
    var lastFrame: ImageFrame? = null
    override suspend fun inspectOutputShape() = outputInfo
    override suspend fun detect(imageFrame: ImageFrame): List<Detection> { lastFrame = imageFrame; return detections }
}

private class FakeCurrencyRecognizerFactory : CurrencyRecognizerFactory {
    val created = mutableListOf<FakeCurrencyRecognizer>()
    override fun create(onResult: (label: String, confidence: Float) -> Unit): CurrencyRecognizerPort =
        FakeCurrencyRecognizer().also(created::add)
}

private class FakeCurrencyRecognizer : CurrencyRecognizerPort {
    var lastFrame: ImageFrame? = null
    var resetCount = 0
    var closed = false
    override fun analyze(imageFrame: ImageFrame) { lastFrame = imageFrame }
    override fun resetBuffer() { resetCount++ }
    override fun close() { closed = true }
}
