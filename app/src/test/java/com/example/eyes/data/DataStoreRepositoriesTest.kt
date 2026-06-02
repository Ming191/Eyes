package com.example.eyes.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.data.navigation.DataStoreNavigationPreferencesRepository
import com.example.eyes.data.settings.DataStoreSettingsRepository
import com.example.eyes.data.voice.DataStoreVoiceCommandRepository
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.voice.VoiceCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DataStoreRepositoriesTest {

    private lateinit var manager: DataStoreManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        File(context.filesDir, "datastore/user_settings.preferences_pb").delete()
        manager = DataStoreManager(context)
    }

    @Test
    fun dataStoreManager_defaultsAndPersistsSettings() = runBlocking {
        assertEquals(1.0f, manager.ttsSpeedFlow.first(), 0.0f)
        assertEquals(0.5f, manager.alertSensitivityFlow.first(), 0.0f)
        assertFalse(manager.onboardingCompletedFlow.first())
        assertEquals(OcrMode.QUICK, manager.ocrModeFlow.first())
        assertFalse(manager.ocrTranslateToVietnameseFlow.first())
        assertTrue(manager.voiceGuideEnabledFlow.first())
        assertNull(manager.lastVoiceCommandFlow.first())

        manager.setTtsSpeed(1.4f)
        manager.setOnboardingCompleted(true)
        manager.setOcrMode(OcrMode.ACCURACY)
        manager.setOcrTranslateToVietnamese(true)
        manager.setAppLanguage(AppLanguage.VI)
        manager.setLastVoiceCommand(VoiceCommand.OpenSettings)

        assertEquals(1.4f, manager.ttsSpeedFlow.first(), 0.0f)
        assertTrue(manager.onboardingCompletedFlow.first())
        assertEquals(OcrMode.ACCURACY, manager.ocrModeFlow.first())
        assertTrue(manager.ocrTranslateToVietnameseFlow.first())
        assertEquals(AppLanguage.VI, manager.appLanguageFlow.first())
        assertEquals(VoiceCommand.OpenSettings, manager.lastVoiceCommandFlow.first())

        manager.clearLastVoiceCommand()

        assertNull(manager.lastVoiceCommandFlow.first())
    }

    @Test
    fun repositoriesDelegateToDataStoreManager() = runBlocking {
        val settings = DataStoreSettingsRepository(manager)
        val navigation = DataStoreNavigationPreferencesRepository(manager)
        val voice = DataStoreVoiceCommandRepository(manager)

        settings.setTtsSpeed(0.8f)
        settings.setOcrTranslateToVietnamese(true)
        settings.setAppLanguage(AppLanguage.EN)
        navigation.setOnboardingCompleted(true)
        navigation.setOcrMode(OcrMode.ACCURACY)
        voice.setLastVoiceCommand(VoiceCommand.ReadText)

        assertEquals(0.8f, settings.ttsSpeedFlow.first(), 0.0f)
        assertTrue(settings.ocrTranslateToVietnameseFlow.first())
        assertEquals(AppLanguage.EN, settings.appLanguageFlow.first())
        assertTrue(navigation.onboardingCompletedFlow.first())
        assertEquals(OcrMode.ACCURACY, navigation.ocrModeFlow.first())
        assertEquals(VoiceCommand.ReadText, voice.lastVoiceCommandFlow.first())

        voice.clearLastVoiceCommand()

        assertNull(voice.lastVoiceCommandFlow.first())
    }
}
