package com.example.eyes.di

import com.example.eyes.camera.CameraManager
import com.example.eyes.data.DataStoreManager
import com.example.eyes.ocr.Gpt4oOcrEngine
import com.example.eyes.ocr.MlKitOcrEngine
import com.example.eyes.ocr.OcrEngine
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.system.TtsService
import com.example.eyes.ui.camera.CameraViewModel
import com.example.eyes.ui.home.HomeViewModel
import com.example.eyes.ui.navigation.AppNavViewModel
import com.example.eyes.ui.ocr.OcrViewModel
import com.example.eyes.ui.settings.SettingsViewModel
import org.koin.core.qualifier.named
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { TtsService(androidContext()) }
    single<SpeechOutput> { get<TtsService>() }
    single { HapticService(androidContext()) }
    single { DataStoreManager(androidContext()) }
    single { CameraManager(androidContext()) }
    factory<OcrEngine>(named("quick-ocr")) { MlKitOcrEngine() }
    factory<OcrEngine>(named("accuracy-ocr")) { Gpt4oOcrEngine() }
    viewModel { AppNavViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { CameraViewModel() }
    viewModel {
        OcrViewModel(
            quickOcrEngine = get(named("quick-ocr")),
            accuracyOcrEngine = get(named("accuracy-ocr")),
            dataStoreManager = get(),
            tts = get(),
            haptic = get()
        )
    }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
