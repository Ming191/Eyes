package com.example.eyes.di

import com.example.eyes.data.DataStoreManager
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { TtsService(androidContext()) }
    single { HapticService(androidContext()) }
    single { DataStoreManager(androidContext()) }
}
