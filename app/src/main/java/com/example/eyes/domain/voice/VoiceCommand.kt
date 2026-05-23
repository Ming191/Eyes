package com.example.eyes.domain.voice

/**
 * Voice command intents recognized by [CommandParser].
 *
 * Each variant corresponds to a user-facing capability of the SoundVision app.
 * The parser maps free-form Vietnamese speech to one of these variants;
 * unrecognized input becomes [Unknown] so the UI can offer guidance.
 */
sealed interface VoiceCommand {

    /** Read printed text from the camera (OCR feature). */
    data object ReadText : VoiceCommand

    /** Describe the surrounding scene (scene description feature). */
    data object DescribeScene : VoiceCommand

    /** Recognize Vietnamese banknote denomination (currency feature). */
    data object RecognizeCurrency : VoiceCommand

    /** Repeat the last spoken response. */
    data object Repeat : VoiceCommand

    /** Stop the current speech and any in-flight voice command. */
    data object Stop : VoiceCommand

    /** List available commands to the user. */
    data object Help : VoiceCommand

    /**
     * Fallback when the parser cannot map [rawText] to any known command.
     * The UI typically responds by listing example commands.
     */
    data class Unknown(val rawText: String) : VoiceCommand
}
