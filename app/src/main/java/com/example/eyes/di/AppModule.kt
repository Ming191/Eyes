package com.example.eyes.di

import android.content.Context
import android.media.AudioManager
import com.example.eyes.BuildConfig
import com.example.eyes.ai.MiDasDepthEstimator
import com.example.eyes.ai.YoloDetector
import com.example.eyes.camera.CameraManager
import com.example.eyes.data.DataStoreManager
import com.example.eyes.data.remote.SceneApi
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.system.TtsService
import com.example.eyes.ui.camera.CameraViewModel
import com.example.eyes.ui.home.HomeViewModel
import com.example.eyes.ui.navigation.AppNavViewModel
import com.example.eyes.ui.settings.SettingsViewModel
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
    single {
        androidContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    single { TtsService(androidContext()) }
    single<SpeechOutput> { get<TtsService>() }
    single { HapticService(androidContext()) }
    single { DataStoreManager(androidContext()) }
    single { CameraManager(androidContext()) }
    factory { YoloDetector(androidContext()) }
    single { MiDasDepthEstimator(androidContext()) }
    single {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SceneApi::class.java)
    }
    single { SceneRepository(androidContext(), get()) }
    viewModel { AppNavViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { CameraViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
