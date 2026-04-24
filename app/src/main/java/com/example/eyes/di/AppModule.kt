package com.example.eyes.di

import com.example.eyes.data.DataStoreManager
import com.example.eyes.camera.CameraManager
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.system.TtsService
import com.example.eyes.ui.camera.CameraViewModel
import com.example.eyes.ui.home.HomeViewModel
import com.example.eyes.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { TtsService(androidContext()) }
    single<SpeechOutput> { get<TtsService>() }
    single { HapticService(androidContext()) }
    single { DataStoreManager(androidContext()) }
    single { CameraManager(androidContext()) }
    viewModel { HomeViewModel(get()) }
    viewModel { CameraViewModel() }
    viewModel { SettingsViewModel(get()) }
}
