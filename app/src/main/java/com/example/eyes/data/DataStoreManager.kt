package com.example.eyes.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.ocr.OcrMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class DataStoreManager(private val context: Context) {

    private object PreferenceKeys {
        val TtsSpeed = floatPreferencesKey("tts_speed")
        val AlertSensitivity = floatPreferencesKey("alert_sensitivity")
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val OcrMode = stringPreferencesKey("ocr_mode")
        val OcrTranslateToVietnamese = booleanPreferencesKey("ocr_translate_to_vi")
        val LastVoiceCommand = stringPreferencesKey("last_voice_command")
    }

    val ttsSpeedFlow: Flow<Float> = context.dataStore.data.map { preferences: Preferences ->
        preferences[PreferenceKeys.TtsSpeed] ?: 1.0f
    }

    val alertSensitivityFlow: Flow<Float> =
        context.dataStore.data.map { preferences: Preferences ->
            preferences[PreferenceKeys.AlertSensitivity] ?: 0.5f
        }

    val onboardingCompletedFlow: Flow<Boolean> =
        context.dataStore.data.map { preferences: Preferences ->
            preferences[PreferenceKeys.OnboardingCompleted] ?: false
        }

    val ocrModeFlow: Flow<OcrMode> = context.dataStore.data.map { preferences: Preferences ->
        val raw = preferences[PreferenceKeys.OcrMode]
        runCatching { if (raw == null) OcrMode.QUICK else OcrMode.valueOf(raw) }
            .getOrDefault(OcrMode.QUICK)
    }

    val ocrTranslateToVietnameseFlow: Flow<Boolean> = context.dataStore.data.map { preferences: Preferences ->
        preferences[PreferenceKeys.OcrTranslateToVietnamese] ?: false
    }

    val lastVoiceCommandFlow: Flow<String?> =
        context.dataStore.data.map { preferences: Preferences ->
            preferences[PreferenceKeys.LastVoiceCommand]
        }

    suspend fun setTtsSpeed(value: Float) {
        context.dataStore.edit { preferences -> preferences[PreferenceKeys.TtsSpeed] = value }
    }

    suspend fun setAlertSensitivity(value: Float) {
        context.dataStore.edit { preferences -> preferences[PreferenceKeys.AlertSensitivity] = value }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferenceKeys.OnboardingCompleted] = completed }
    }

    suspend fun setOcrMode(mode: OcrMode) {
        context.dataStore.edit { preferences -> preferences[PreferenceKeys.OcrMode] = mode.name }
    }

    suspend fun setOcrTranslateToVietnamese(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferenceKeys.OcrTranslateToVietnamese] = enabled }
    }

    suspend fun setLastVoiceCommand(command: VoiceCommand) {
        context.dataStore.edit { preferences -> preferences[PreferenceKeys.LastVoiceCommand] = encodeVoiceCommand(command) }
    }

    suspend fun clearLastVoiceCommand() {
        context.dataStore.edit { preferences -> preferences.remove(PreferenceKeys.LastVoiceCommand) }
    }

    private companion object {
        /**
         * Stable string encoding for [VoiceCommand]. The format is intentionally
         * simple (prefix or "prefix:payload") so it survives sealed-type changes
         * and can be read by other modules without depending on this class.
         *
         *   READ_TEXT
         *   DESCRIBE_SCENE
         *   RECOGNIZE_CURRENCY
         *   DETECT_OBSTACLE
         *   REPEAT
         *   STOP
         *   HELP
         *   NAVIGATE:<destination>
         *   UNKNOWN
         */
        fun encodeVoiceCommand(command: VoiceCommand): String = when (command) {
            VoiceCommand.ReadText -> "READ_TEXT"
            VoiceCommand.DescribeScene -> "DESCRIBE_SCENE"
            VoiceCommand.RecognizeCurrency -> "RECOGNIZE_CURRENCY"
            VoiceCommand.DetectObstacle -> "DETECT_OBSTACLE"
            VoiceCommand.Repeat -> "REPEAT"
            VoiceCommand.Stop -> "STOP"
            VoiceCommand.Help -> "HELP"
            is VoiceCommand.Navigate -> "NAVIGATE:${command.destination}"
            is VoiceCommand.Unknown -> "UNKNOWN"
        }
    }
}
