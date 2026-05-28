package com.example.eyes.application.voice

import com.example.eyes.domain.i18n.AppLanguage

interface VoiceCommandTextProvider {
    fun text(language: AppLanguage): VoiceCommandText
}

data class VoiceCommandText(
    val readText: String,
    val describeScene: String,
    val recognizeCurrency: String,
    val nothingToRepeat: String,
    val stopped: String,
    val help: String,
    val unknown: String
)
