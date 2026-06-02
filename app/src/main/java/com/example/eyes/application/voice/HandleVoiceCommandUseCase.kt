package com.example.eyes.application.voice

import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.application.settings.UpdateSettingsUseCase
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.voice.VoiceCameraTarget
import com.example.eyes.domain.voice.VoiceIntent
import kotlinx.coroutines.flow.first

enum class VoiceNavigationTargetKind {
    Camera,
    Home,
    Settings,
    Emergency
}

data class VoiceCommandAction(
    val navigationTarget: VoiceNavigationTargetKind? = null,
    val cameraTarget: VoiceCameraTarget? = null,
    val ocrMode: OcrMode? = null,
    val autoCapture: Boolean = false,
    val dialNumber: String? = null,
    val shouldRestartListening: Boolean = false,
    val shouldExpandHelp: Boolean = false
)

class HandleVoiceCommandUseCase(
    private val speechOutput: SpeechOutput,
    private val voiceCommandTextProvider: VoiceCommandTextProvider,
    private val updateSettings: UpdateSettingsUseCase,
    private val settingsRepository: SettingsRepository
) {
    private var lastSpokenText: String = ""

    suspend operator fun invoke(intent: VoiceIntent, language: AppLanguage): VoiceCommandAction {
        val text = voiceCommandTextProvider.text(language)

        return when (intent) {
            is VoiceIntent.OpenCamera -> {
                speakAndRemember(openCameraText(intent.target, intent.ocrMode, text), language)
                VoiceCommandAction(
                    navigationTarget = VoiceNavigationTargetKind.Camera,
                    cameraTarget = intent.target,
                    ocrMode = intent.ocrMode
                )
            }

            is VoiceIntent.CaptureCamera -> {
                speakAndRemember(captureCameraText(intent.target, intent.ocrMode, text), language)
                VoiceCommandAction(
                    navigationTarget = VoiceNavigationTargetKind.Camera,
                    cameraTarget = intent.target,
                    ocrMode = intent.ocrMode,
                    autoCapture = true
                )
            }

            VoiceIntent.OpenHome -> {
                speakAndRemember(text.openHome, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Home)
            }

            VoiceIntent.OpenSettings -> {
                speakAndRemember(text.openSettings, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Settings)
            }

            VoiceIntent.OpenEmergencyList -> {
                speakAndRemember(text.openEmergency, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Emergency)
            }

            is VoiceIntent.DialEmergency -> {
                val announcement = voiceCommandTextProvider.dialEmergency(language, intent.number)
                speakAndRemember(announcement, language)
                VoiceCommandAction(dialNumber = intent.number)
            }

            is VoiceIntent.SetSpeechSpeed -> {
                val speed = intent.speed.nearestTtsSpeedPreset()
                updateSettings.setTtsSpeed(speed)
                val announcement = ttsSpeedAnnouncement(speed, language)
                speakAndRemember(announcement, language)
                VoiceCommandAction()
            }

            VoiceIntent.IncreaseSpeechSpeed -> {
                val speed = settingsRepository.ttsSpeedFlow.first().nextTtsSpeedPreset()
                updateSettings.setTtsSpeed(speed)
                val announcement = ttsSpeedAnnouncement(speed, language)
                speakAndRemember(announcement, language)
                VoiceCommandAction()
            }

            VoiceIntent.DecreaseSpeechSpeed -> {
                val speed = settingsRepository.ttsSpeedFlow.first().previousTtsSpeedPreset()
                updateSettings.setTtsSpeed(speed)
                val announcement = ttsSpeedAnnouncement(speed, language)
                speakAndRemember(announcement, language)
                VoiceCommandAction()
            }

            is VoiceIntent.SetAppLanguage -> {
                updateSettings.setAppLanguage(intent.language)
                val announcement = voiceCommandTextProvider.appLanguageChanged(intent.language)
                lastSpokenText = announcement
                speechOutput.speakAndAwait(announcement, intent.language.ttsLocale)
                VoiceCommandAction()
            }

            is VoiceIntent.SetAutoTranslate -> {
                updateSettings.setAutoTranslateEnglishOcrToVietnamese(intent.enabled)
                val announcement = if (intent.enabled) text.autoTranslateEnabled else text.autoTranslateDisabled
                speakAndRemember(announcement, language)
                VoiceCommandAction()
            }

            VoiceIntent.Stop -> {
                speakAndRemember(text.stop, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Home)
            }

            VoiceIntent.Repeat -> {
                if (lastSpokenText.isBlank()) {
                    speechOutput.speakAndAwait(text.nothingToRepeat, language.ttsLocale)
                } else {
                    speechOutput.speakAndAwait(lastSpokenText, language.ttsLocale)
                }
                VoiceCommandAction(shouldRestartListening = true)
            }

            VoiceIntent.Help -> {
                speakAndRemember(text.help, language)
                VoiceCommandAction(
                    shouldRestartListening = true,
                    shouldExpandHelp = true
                )
            }

            is VoiceIntent.Unknown -> {
                speakAndRemember(text.unknown, language)
                VoiceCommandAction(shouldRestartListening = true)
            }
        }
    }

    private fun openCameraText(target: VoiceCameraTarget, ocrMode: OcrMode?, text: VoiceCommandText): String = when (target) {
        VoiceCameraTarget.OCR -> when (ocrMode) {
            OcrMode.ACCURACY -> text.ocrAccurate
            OcrMode.QUICK,
            null -> text.ocrQuick
        }
        VoiceCameraTarget.SCENE_DESCRIPTION -> text.describeScene
        VoiceCameraTarget.OBJECT_DETECTION -> text.detectObjects
        VoiceCameraTarget.CURRENCY -> text.recognizeCurrency
    }

    private fun captureCameraText(target: VoiceCameraTarget, ocrMode: OcrMode?, text: VoiceCommandText): String = when (target) {
        VoiceCameraTarget.OCR -> when (ocrMode) {
            OcrMode.ACCURACY -> text.captureOcrAccurate
            OcrMode.QUICK,
            null -> text.captureOcrQuick
        }
        VoiceCameraTarget.SCENE_DESCRIPTION -> text.captureScene
        VoiceCameraTarget.OBJECT_DETECTION -> text.detectObjects
        VoiceCameraTarget.CURRENCY -> text.captureCurrency
    }

    private fun ttsSpeedAnnouncement(speed: Float, language: AppLanguage): String {
        val speedLabel = String.format(language.ttsLocale, "%.2f", speed)
        return voiceCommandTextProvider.ttsSpeedChanged(language, speedLabel)
    }

    private suspend fun speakAndRemember(text: String, language: AppLanguage) {
        lastSpokenText = text
        speechOutput.speakAndAwait(text, language.ttsLocale)
    }

    private fun Float.nearestTtsSpeedPreset(): Float =
        TTS_SPEED_PRESETS.minByOrNull { kotlin.math.abs(it - this) } ?: 1.0f

    private fun Float.previousTtsSpeedPreset(): Float {
        val currentIndex = nearestTtsSpeedPresetIndex()
        return TTS_SPEED_PRESETS[(currentIndex - 1).coerceAtLeast(0)]
    }

    private fun Float.nextTtsSpeedPreset(): Float {
        val currentIndex = nearestTtsSpeedPresetIndex()
        return TTS_SPEED_PRESETS[(currentIndex + 1).coerceAtMost(TTS_SPEED_PRESETS.lastIndex)]
    }

    private fun Float.nearestTtsSpeedPresetIndex(): Int =
        TTS_SPEED_PRESETS.indices.minByOrNull { index -> kotlin.math.abs(TTS_SPEED_PRESETS[index] - this) } ?: DEFAULT_TTS_SPEED_INDEX

    private companion object {
        private val TTS_SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
        private const val DEFAULT_TTS_SPEED_INDEX = 2
    }
}
