package com.example.eyes.domain.voice

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode

sealed interface VoiceIntent {
    data class OpenCamera(
        val target: VoiceCameraTarget,
        val ocrMode: OcrMode? = null
    ) : VoiceIntent

    data class CaptureCamera(
        val target: VoiceCameraTarget,
        val ocrMode: OcrMode? = null
    ) : VoiceIntent

    data object OpenHome : VoiceIntent
    data object OpenSettings : VoiceIntent
    data object OpenEmergencyList : VoiceIntent
    data class DialEmergency(val number: String) : VoiceIntent
    data class SetSpeechSpeed(val speed: Float) : VoiceIntent
    data object IncreaseSpeechSpeed : VoiceIntent
    data object DecreaseSpeechSpeed : VoiceIntent
    data class SetAppLanguage(val language: AppLanguage) : VoiceIntent
    data class SetAutoTranslate(val enabled: Boolean) : VoiceIntent
    data object Repeat : VoiceIntent
    data object Help : VoiceIntent
    data object Stop : VoiceIntent
    data class Unknown(val rawText: String) : VoiceIntent
}
