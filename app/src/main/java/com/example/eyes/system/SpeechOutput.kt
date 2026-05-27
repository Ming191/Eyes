package com.example.eyes.system

import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Abstraction for text-to-speech output. Allows ViewModels and other modules
 * to request speech without coupling to the concrete TtsService implementation.
 */
interface SpeechOutput {

    val currentSpokenText: Flow<String?>
        get() = emptyFlow()

    /** Speak [text]. */
    fun speak(text: String)

    /** Speak [text] with a requested locale. */
    fun speak(text: String, locale: Locale) {
        speak(text)
    }

    /** Speak [text] and resume when the utterance is done, stopped, or fails. */
    suspend fun speakAndAwait(text: String) {
        speak(text)
    }

    /** Speak [text] with [locale] and resume when best-effort speech starts. */
    suspend fun speakAndAwait(text: String, locale: Locale) {
        speak(text, locale)
    }

    /** Update the TTS engine speech rate. Range typically 0.5f..2.0f. */
    fun setSpeechRate(rate: Float) = Unit

    /** Stop any in-flight or queued speech. */
    fun stop() = Unit
}
