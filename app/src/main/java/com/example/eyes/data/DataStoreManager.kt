package com.example.eyes.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        val EmergencyPhoneNumber = stringPreferencesKey("emergency_phone_number")
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

    val emergencyPhoneNumberFlow: Flow<String> = context.dataStore.data.map { preferences: Preferences ->
        preferences[PreferenceKeys.EmergencyPhoneNumber] ?: DEFAULT_EMERGENCY_PHONE_NUMBER
    }

    suspend fun setTtsSpeed(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.TtsSpeed] = value
        }
    }

    suspend fun setAlertSensitivity(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AlertSensitivity] = value
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.OnboardingCompleted] = completed
        }
    }

    suspend fun setOcrMode(mode: OcrMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.OcrMode] = mode.name
        }
    }

    suspend fun setOcrTranslateToVietnamese(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.OcrTranslateToVietnamese] = enabled
        }
    }

    suspend fun setEmergencyPhoneNumber(phoneNumber: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.EmergencyPhoneNumber] = phoneNumber.filter { it.isDigit() || it == '+' }
        }
    }

    companion object {
        const val DEFAULT_EMERGENCY_PHONE_NUMBER = "115"
    }
}
