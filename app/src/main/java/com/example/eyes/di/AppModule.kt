package com.example.eyes.di

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.eyes.application.home.AnnounceHomeGreetingUseCase
import com.example.eyes.application.home.BuildHomeStateUseCase
import com.example.eyes.application.home.HomeTextProvider
import com.example.eyes.application.navigation.AnnounceDestinationUseCase
import com.example.eyes.application.navigation.ApplySpeechRateUseCase
import com.example.eyes.application.navigation.CompleteOnboardingUseCase
import com.example.eyes.application.navigation.ObserveAppNavStateUseCase
import com.example.eyes.application.navigation.SetCameraOcrModeUseCase
import com.example.eyes.application.navigation.UpdateAppLanguageUseCase
import com.example.eyes.application.objectdetection.DetectObjectsUseCase
import com.example.eyes.application.objectdetection.WarmUpObjectDetectionUseCase
import com.example.eyes.application.ocr.RecognizeOcrDocumentUseCase
import com.example.eyes.application.ports.ObjectDetectorPort
import com.example.eyes.application.ports.OcrEnginePort
import com.example.eyes.application.ports.OcrTranslatorPort
import com.example.eyes.application.scene.DescribeSceneUseCase
import com.example.eyes.application.settings.ObserveSettingsUseCase
import com.example.eyes.application.settings.UpdateSettingsUseCase
import com.example.eyes.application.voice.HandleVoiceCommandUseCase
import com.example.eyes.infrastructure.camera.CameraManager
import com.example.eyes.data.DataStoreManager
import com.example.eyes.data.i18n.AndroidHomeTextProvider
import com.example.eyes.data.navigation.DataStoreNavigationPreferencesRepository
import com.example.eyes.data.scene.SceneRepositorySceneDescriptionRepository
import com.example.eyes.data.settings.DataStoreSettingsRepository
import com.example.eyes.data.voice.DataStoreVoiceCommandRepository
import com.example.eyes.domain.navigation.NavigationPreferencesRepository
import com.example.eyes.domain.scene.SceneDescriptionRepository
import com.example.eyes.domain.settings.SettingsRepository
import com.example.eyes.domain.voice.VoiceCommandRepository
import com.example.eyes.domain.voice.CommandParser
import com.example.eyes.domain.accessibility.AnnouncementController
import com.example.eyes.data.remote.Gpt4oSceneDescriptionEngine
import com.example.eyes.i18n.AndroidLocalizedTextProvider
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.infrastructure.openai.Gpt4oOcrEngine
import com.example.eyes.infrastructure.openai.GptTranslationEngine
import com.example.eyes.infrastructure.ocr.MlKitOcrEngine
import com.example.eyes.ocr.MlKitOcrGuidanceAnalyzer
import com.example.eyes.data.remote.SceneDescriptionEngine
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.infrastructure.objectdetection.ExecutorchModelAssetCopier
import com.example.eyes.infrastructure.objectdetection.YoloExecutorchDetector
import com.example.eyes.infrastructure.objectdetection.YoloExecutorchModelLoader
import com.example.eyes.infrastructure.system.HapticService
import com.example.eyes.infrastructure.system.SpeechOutput
import com.example.eyes.infrastructure.system.SttService
import com.example.eyes.infrastructure.system.TtsService
import com.example.eyes.ui.camera.CameraViewModel
import com.example.eyes.ui.home.HomeViewModel
import com.example.eyes.ui.navigation.AppNavViewModel
import com.example.eyes.ui.settings.SettingsViewModel
import com.example.eyes.voiceguide.AccessibilityStateProvider
import com.example.eyes.voiceguide.AndroidAccessibilityStateProvider
import com.example.eyes.voiceguide.ApplicationScope
import com.example.eyes.voiceguide.DefaultAnnouncementController
import org.koin.core.qualifier.named
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<LocalizedTextProvider> { AndroidLocalizedTextProvider(androidContext()) }
    single<HomeTextProvider> { AndroidHomeTextProvider(get()) }
    single {
        androidContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    single { TtsService(androidContext()) }
    single<SpeechOutput> { get<TtsService>() }
    single<com.example.eyes.domain.speech.SpeechOutput> { get<TtsService>() }
    single { ApplicationScope() }
    single<AccessibilityStateProvider> { AndroidAccessibilityStateProvider(androidContext()) }
    single<AnnouncementController> { DefaultAnnouncementController(get<DataStoreManager>().voiceGuideEnabledFlow, get(), get(), get()) }
    single { HapticService(androidContext()) }
    single { DataStoreManager(androidContext()) }
    single<SettingsRepository> { DataStoreSettingsRepository(get()) }
    single<NavigationPreferencesRepository> { DataStoreNavigationPreferencesRepository(get()) }
    single<VoiceCommandRepository> { DataStoreVoiceCommandRepository(get()) }
    factory { ObserveSettingsUseCase(get()) }
    factory { UpdateSettingsUseCase(get(), get()) }
    single { CameraManager(androidContext()) }
    factory { SttService(androidContext()) }
    single { CommandParser() }
    factory { BuildHomeStateUseCase(get()) }
    factory { AnnounceHomeGreetingUseCase(get(), get(), get()) }
    factory { ObserveAppNavStateUseCase(get(), get()) }
    factory { CompleteOnboardingUseCase(get()) }
    factory { UpdateAppLanguageUseCase(get()) }
    factory { ApplySpeechRateUseCase(get(), get()) }
    factory { SetCameraOcrModeUseCase(get()) }
    factory { AnnounceDestinationUseCase(get(), get()) }
    factory { HandleVoiceCommandUseCase(get(), get(), get()) }
    factory<OcrEnginePort>(named("quick-ocr")) { MlKitOcrEngine() }
    factory<OcrEnginePort>(named("accuracy-ocr")) { Gpt4oOcrEngine() }
    factory { MlKitOcrGuidanceAnalyzer() }
    factory<OcrTranslatorPort> { GptTranslationEngine() }
    factory { RecognizeOcrDocumentUseCase(get(named("quick-ocr")), get(named("accuracy-ocr")), get()) }
    single { ExecutorchModelAssetCopier(androidContext()) }
    single { YoloExecutorchModelLoader(get()) }
    single { YoloExecutorchDetector(get()) }
    single<ObjectDetectorPort> { get<YoloExecutorchDetector>() }
    factory { DetectObjectsUseCase(get()) }
    factory { WarmUpObjectDetectionUseCase(get()) }
    single<SceneDescriptionEngine> { Gpt4oSceneDescriptionEngine() }
    single {
        SceneRepository(
            sceneDescriptionEngine = get(),
            networkChecker = {
                val connectivityManager = androidContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                if (network == null) {
                    false
                } else {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }
            }
        )
    }
    single<SceneDescriptionRepository> { SceneRepositorySceneDescriptionRepository(get()) }
    factory { DescribeSceneUseCase(get()) }
    viewModel {
        AppNavViewModel(
            observeAppNavState = get(),
            completeOnboardingUseCase = get(),
            updateAppLanguageUseCase = get(),
            applySpeechRateUseCase = get(),
            setCameraOcrModeUseCase = get(),
            announceDestinationUseCase = get(),
            speechOutput = get()
        )
    }
    viewModel {
        HomeViewModel(
            buildHomeState = get(),
            announceHomeGreeting = get(),
            settingsRepository = get()
        )
    }
    viewModel {
        CameraViewModel(
            recognizeOcrDocumentUseCase = get(),
            ocrGuidanceAnalyzer = get(),
            ttsService = get(),
            hapticService = get(),
            dataStoreManager = get(),
            voiceCommandRepository = get(),
            describeSceneUseCase = get(),
            detectObjectsUseCase = get(),
            warmUpObjectDetectionUseCase = get(),
            audioManager = get(),
            localizedTextProvider = get()
        )
    }
    viewModel {
        SettingsViewModel(
            observeSettings = get(),
            updateSettings = get()
        )
    }
    viewModel { com.example.eyes.ui.voice.VoiceCommandViewModel(get(), get(), get(), get(), get()) }
}
