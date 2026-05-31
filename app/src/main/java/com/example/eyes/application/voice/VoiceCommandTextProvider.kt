package com.example.eyes.application.voice

import com.example.eyes.domain.i18n.AppLanguage

interface VoiceCommandTextProvider {
    fun text(language: AppLanguage): VoiceCommandText
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
    val nothingToRepeat: String,
    val help: String,
    val unknown: String
)
