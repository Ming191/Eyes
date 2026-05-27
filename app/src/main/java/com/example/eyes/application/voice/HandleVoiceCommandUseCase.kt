package com.example.eyes.application.voice

import com.example.eyes.R
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.domain.voice.VoiceCommandRepository
import com.example.eyes.domain.speech.SpeechOutput
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider

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
    private val localizedTextProvider: LocalizedTextProvider
) {
    private var lastSpokenText: String = ""

    suspend operator fun invoke(command: VoiceCommand, language: AppLanguage): VoiceCommandAction {
        voiceCommandRepository.setLastVoiceCommand(command)
        val text = voiceText(language)

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

    private fun voiceText(language: AppLanguage): VoiceText = VoiceText(
        readText = localizedTextProvider.getString(R.string.voice_vm_read_text_ack, language),
        describeScene = localizedTextProvider.getString(R.string.voice_vm_describe_scene_ack, language),
        recognizeCurrency = localizedTextProvider.getString(R.string.voice_vm_recognize_currency_ack, language),
        nothingToRepeat = localizedTextProvider.getString(R.string.voice_vm_nothing_to_repeat, language),
        stopped = localizedTextProvider.getString(R.string.voice_vm_stopped_ack, language),
        help = localizedTextProvider.getString(R.string.voice_vm_help_text, language),
        unknown = localizedTextProvider.getString(R.string.voice_vm_unknown_command, language)
    )

    private suspend fun speakAndRemember(text: String, language: AppLanguage) {
        lastSpokenText = text
        speechOutput.speakAndAwait(text, language.ttsLocale)
    }

    private data class VoiceText(
        val readText: String,
        val describeScene: String,
        val recognizeCurrency: String,
        val nothingToRepeat: String,
        val stopped: String,
        val help: String,
        val unknown: String
    )
}
