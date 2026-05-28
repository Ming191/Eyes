package com.example.eyes.domain.speech

import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface SpeechOutput {
    val currentSpokenText: Flow<String?>
        get() = emptyFlow()

    fun speak(text: String)

    fun speak(text: String, locale: Locale) {
        speak(text)
    }

    suspend fun speakAndAwait(text: String) {
        speak(text)
    }

    suspend fun speakAndAwait(text: String, locale: Locale) {
        speak(text, locale)
    }

    fun setSpeechRate(rate: Float) = Unit

    fun warmupLocale(locale: Locale) = Unit

    fun stop() = Unit
}
