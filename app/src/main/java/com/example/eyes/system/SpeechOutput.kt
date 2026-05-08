package com.example.eyes.system

/**
 * Abstraction for text-to-speech output. Allows ViewModels and other modules
 * to request speech without coupling to the concrete TtsService implementation.
 *
 * Priority semantics:
 *  - URGENT: Interrupts current speech (queue flushed). Use for obstacle alerts
 *           and other safety-critical announcements.
 *  - HIGH:   Plays after current utterance, before any NORMAL utterances.
 *  - NORMAL: Plays in FIFO order. Default for routine feedback.
 */
interface SpeechOutput {

    enum class Priority { URGENT, HIGH, NORMAL }

    /** Speak [text] at NORMAL priority. */
    fun speak(text: String)

    /** Speak [text] at the given [priority]. */
    fun speak(text: String, priority: Priority)

    /** Update the TTS engine speech rate. Range typically 0.5f..2.0f. */
    fun setSpeechRate(rate: Float) = Unit

    /** Stop any in-flight or queued speech. */
    fun stop() = Unit
}