package com.example.eyes.application.voice

import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.application.ports.VoiceCommandRepository
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.domain.i18n.AppLanguage

enum class VoiceNavigationTargetKind {
    Camera,
    Home
}

data class VoiceCommandAction(
    val navigationTarget: VoiceNavigationTargetKind? = null,
    val shouldRestartListening: Boolean = false,
    val shouldExpandHelp: Boolean = false
)

class HandleVoiceCommandUseCase(
    private val voiceCommandRepository: VoiceCommandRepository,
    private val speechOutput: SpeechOutput,
    private val voiceCommandTextProvider: VoiceCommandTextProvider
) {
    private var lastSpokenText: String = ""

    suspend operator fun invoke(command: VoiceCommand, language: AppLanguage): VoiceCommandAction {
        voiceCommandRepository.setLastVoiceCommand(command)
        val text = voiceCommandTextProvider.text(language)

        return when (command) {
            VoiceCommand.ReadText -> {
                speakAndRemember(text.readText, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Camera)
            }

            VoiceCommand.DescribeScene -> {
                speakAndRemember(text.describeScene, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Camera)
            }

            VoiceCommand.RecognizeCurrency -> {
                speakAndRemember(text.recognizeCurrency, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Camera)
            }

            VoiceCommand.Repeat -> {
                if (lastSpokenText.isBlank()) {
                    speechOutput.speakAndAwait(text.nothingToRepeat, language.ttsLocale)
                } else {
                    speechOutput.speakAndAwait(lastSpokenText, language.ttsLocale)
                }
                VoiceCommandAction(shouldRestartListening = true)
            }

            VoiceCommand.Stop -> {
                speechOutput.stop()
                speakAndRemember(text.stopped, language)
                VoiceCommandAction(navigationTarget = VoiceNavigationTargetKind.Home)
            }

            VoiceCommand.Help -> {
                speakAndRemember(text.help, language)
                VoiceCommandAction(
                    shouldRestartListening = true,
                    shouldExpandHelp = true
                )
            }

            is VoiceCommand.Unknown -> {
                speakAndRemember(text.unknown, language)
                VoiceCommandAction(shouldRestartListening = true)
            }
        }
    }

    private suspend fun speakAndRemember(text: String, language: AppLanguage) {
        lastSpokenText = text
        speechOutput.speakAndAwait(text, language.ttsLocale)
    }
}
