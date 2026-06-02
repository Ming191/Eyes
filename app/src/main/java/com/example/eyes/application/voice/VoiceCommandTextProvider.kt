package com.example.eyes.application.voice

import com.example.eyes.domain.i18n.AppLanguage

interface VoiceCommandTextProvider {
    fun text(language: AppLanguage): VoiceCommandText
    fun ttsSpeedChanged(language: AppLanguage, speedLabel: String): String
    fun appLanguageChanged(language: AppLanguage): String
    fun dialEmergency(language: AppLanguage, number: String): String
}

data class VoiceCommandText(
    val readText: String,
    val describeScene: String,
    val recognizeCurrency: String,
    val detectObjects: String,
    val openHome: String,
    val openSettings: String,
    val openEmergency: String,
    val ocrQuick: String,
    val ocrAccurate: String,
    val captureOcrQuick: String,
    val captureOcrAccurate: String,
    val captureScene: String,
    val captureCurrency: String,
    val autoTranslateEnabled: String,
    val autoTranslateDisabled: String,
    val stop: String,
    val nothingToRepeat: String,
    val help: String,
    val unknown: String
)
