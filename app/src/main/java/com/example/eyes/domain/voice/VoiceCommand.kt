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

    data object DetectObjects : VoiceCommand

    data object OpenHome : VoiceCommand

    data object OpenSettings : VoiceCommand

    data object OpenEmergency : VoiceCommand


    data object OcrQuick : VoiceCommand

    data object OcrAccurate : VoiceCommand

    /** Stop current voice interaction flow and return to a safe idle state. */
    data object Stop : VoiceCommand

    /** Repeat the last spoken response. */
    data object Repeat : VoiceCommand

    /** List available commands to the user. */
    data object Help : VoiceCommand

    /**
     * Fallback when the parser cannot map [rawText] to any known command.
     * The UI typically responds by listing example commands.
     */
    data class Unknown(val rawText: String) : VoiceCommand
}
