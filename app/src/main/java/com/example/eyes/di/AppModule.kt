package com.example.eyes.di

import android.content.Context
import android.media.AudioManager
import com.example.eyes.camera.CameraManager
import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.voice.CommandParser
import com.example.eyes.data.remote.Gpt4oSceneDescriptionEngine
import com.example.eyes.i18n.AndroidLocalizedTextProvider
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.ocr.Gpt4oOcrEngine
import com.example.eyes.ocr.GptTranslationEngine
import com.example.eyes.ocr.MlKitOcrEngine
import com.example.eyes.ocr.MlKitOcrGuidanceAnalyzer
import com.example.eyes.ocr.OcrEngine
import com.example.eyes.ocr.OcrTranslator
import com.example.eyes.data.remote.SceneDescriptionEngine
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.objectdetection.ExecutorchModelAssetCopier
import com.example.eyes.objectdetection.ObjectDetector
import com.example.eyes.objectdetection.YoloExecutorchDetector
import com.example.eyes.objectdetection.YoloExecutorchModelLoader
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.system.SttService
import com.example.eyes.system.TtsService
import com.example.eyes.ui.camera.CameraViewModel
import com.example.eyes.ui.home.HomeViewModel
import com.example.eyes.ui.navigation.AppNavViewModel
import com.example.eyes.ui.settings.SettingsViewModel
import com.example.eyes.voiceguide.AccessibilityStateProvider
import com.example.eyes.voiceguide.AndroidAccessibilityStateProvider
import com.example.eyes.voiceguide.AnnouncementController
import com.example.eyes.voiceguide.ApplicationScope
import com.example.eyes.voiceguide.DefaultAnnouncementController
import org.koin.core.qualifier.named
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<LocalizedTextProvider> { AndroidLocalizedTextProvider(androidContext()) }
    single {
        androidContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    single { TtsService(androidContext()) }
    single<SpeechOutput> { get<TtsService>() }
    single { ApplicationScope() }
    single<AccessibilityStateProvider> { AndroidAccessibilityStateProvider(androidContext()) }
    single<AnnouncementController> { DefaultAnnouncementController(get<DataStoreManager>().voiceGuideEnabledFlow, get(), get(), get()) }
    single { HapticService(androidContext()) }
    single { DataStoreManager(androidContext()) }
    single { CameraManager(androidContext()) }
    factory { SttService(androidContext()) }
    single { CommandParser() }
    factory<OcrEngine>(named("quick-ocr")) { MlKitOcrEngine() }
    factory<OcrEngine>(named("accuracy-ocr")) { Gpt4oOcrEngine() }
    factory { MlKitOcrGuidanceAnalyzer() }
    factory<OcrTranslator> { GptTranslationEngine() }
    single { ExecutorchModelAssetCopier(androidContext()) }
    single { YoloExecutorchModelLoader(get()) }
    single { YoloExecutorchDetector(get()) }
    single<ObjectDetector> { get<YoloExecutorchDetector>() }
    single<SceneDescriptionEngine> { Gpt4oSceneDescriptionEngine() }
    single { SceneRepository(get(), get()) }
    viewModel {
        AppNavViewModel(
            dataStoreManager = get(),
            speechOutput = get(),
            announcementController = get(),
            localizedTextProvider = get()
        )
    }
    viewModel {
        HomeViewModel(
            localizedTextProvider = get(),
            tts = get(),
            dataStoreManager = get(),
            announcementController = get()
        )
    }
    viewModel {
        CameraViewModel(
            quickOcrEngine = get(named("quick-ocr")),
            accuracyOcrEngine = get(named("accuracy-ocr")),
            ocrGuidanceAnalyzer = get(),
            translator = get(),
            ttsService = get(),
            hapticService = get(),
            dataStoreManager = get(),
            sceneRepository = get(),
            objectDetector = get(),
            audioManager = get(),
            localizedTextProvider = get()
        )
    }
    viewModel {
        SettingsViewModel(
            dataStoreManager = get(),
            speechOutput = get()
        )
    }
    viewModel { com.example.eyes.ui.voice.VoiceCommandViewModel(get(), get(), get(), get(), get(), get()) }
}
