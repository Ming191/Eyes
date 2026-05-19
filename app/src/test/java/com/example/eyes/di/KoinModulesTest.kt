package com.example.eyes.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class KoinModulesTest : KoinTest {

    @Before
    fun setUp() {
        runCatching { stopKoin() }
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun appModule_startsAndCoreResolves_withoutCrash() {
        // GIVEN
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()

        // WHEN
        runCatching { stopKoin() }
        startKoin {
            androidContext(appContext)
            modules(appModule)
        }

        // THEN
        assertNotNull(get<TtsService>())
        assertNotNull(get<HapticService>())
        assertNotNull(get<com.example.eyes.data.DataStoreManager>())
        assertNotNull(get<com.example.eyes.ui.navigation.AppNavViewModel>())
        assertNotNull(get<com.example.eyes.ui.home.HomeViewModel>())
        assertNotNull(get<com.example.eyes.system.SttService>())
        assertNotNull(get<com.example.eyes.domain.voice.CommandParser>())
        assertNotNull(get<com.example.eyes.ui.settings.SettingsViewModel>())
    }

    @Test
    fun ttsServiceAndHapticService_resolveFromKoin() {
        // GIVEN
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()

        // WHEN
        runCatching { stopKoin() }
        startKoin {
            androidContext(appContext)
            modules(appModule)
        }

        val ttsService: TtsService = get()
        val hapticService: HapticService = get()

        // THEN
        assertNotNull(ttsService)
        assertNotNull(hapticService)
    }
}
