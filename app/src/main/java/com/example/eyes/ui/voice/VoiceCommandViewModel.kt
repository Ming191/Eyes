package com.example.eyes.ui.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.voice.CommandParser
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.system.SttErrorReason
import com.example.eyes.system.SttResult
import com.example.eyes.system.SttService
import com.example.eyes.system.SttState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where the user should be sent after the recognizer produces a command.
 * Voice screen collects these as one-shot events and triggers navigation.
 */
sealed interface VoiceNavigationTarget {
    data object Camera : VoiceNavigationTarget
    data object Home : VoiceNavigationTarget // for Stop command
}

data class VoiceCommandUiState(
    val sttState: SttState = SttState.Idle,
    val partialText: String = "",
    val finalText: String = "",
    val lastCommand: VoiceCommand? = null,
    val helpExpanded: Boolean = false
)

class VoiceCommandViewModel(
    private val sttService: SttService,
    private val commandParser: CommandParser,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticService,
    private val dataStoreManager: DataStoreManager,
    private val localizedTextProvider: LocalizedTextProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceCommandUiState())
    val uiState: StateFlow<VoiceCommandUiState> = _uiState.asStateFlow()

    // One-shot navigation events - collected by the screen and consumed.
    private val _navigation = Channel<VoiceNavigationTarget>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    /** Text of the last response we spoke, used by the Repeat command. */
    private var lastSpokenText: String = ""
    private var appLanguage: AppLanguage = AppLanguage.VI
    private var isAppLanguageLoaded = false
    private var pendingStartListening = false

    init {
        // Mirror recognizer state into UI state.
        viewModelScope.launch {
            sttService.state.collect { sttState ->
                _uiState.update { it.copy(sttState = sttState) }
            }
        }

        // React to recognizer results.
        viewModelScope.launch {
            sttService.results.collect { result ->
                handleSttResult(result)
            }
        }

        viewModelScope.launch {
            dataStoreManager.appLanguageFlow.collect { language ->
                appLanguage = language
                isAppLanguageLoaded = true
                if (pendingStartListening) {
                    pendingStartListening = false
                    startListening()
                }
            }
        }
    }

    /**
     * Called when the screen is first composed. Does NOT speak a greeting -
     * doing so racing audio focus with the SpeechRecognizer and gets the
     * greeting silently dropped on most devices. The on-screen status text
     * and a haptic confirm convey "we're listening".
     */
    fun onScreenShown() {
        startListening()
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        if (granted) {
            startListening()
        } else {
            _uiState.update { it.copy(sttState = SttState.Error(SttErrorReason.PermissionDenied)) }
            hapticService.error()
        }
    }

    fun startListening() {
        if (!isAppLanguageLoaded) {
            pendingStartListening = true
            return
        }
        if (_uiState.value.sttState != SttState.Idle && _uiState.value.sttState !is SttState.Error) return
        hapticService.confirm()
        _uiState.update { it.copy(partialText = "", finalText = "", lastCommand = null) }
        sttService.startListening(appLanguage)
    }

    fun stopListening() {
        sttService.stopListening()
    }

    fun handleRecognizedText(text: String) {
        val command = commandParser.parse(text, appLanguage)
        _uiState.update {
            it.copy(
                sttState = SttState.Idle,
                partialText = "",
                finalText = text,
                lastCommand = command
            )
        }
        handleParsedCommand(command)
    }

    fun handleRecognitionCancelled() {
        _uiState.update { it.copy(sttState = SttState.Error(SttErrorReason.NoMatch)) }
        hapticService.error()
    }

    fun handleRecognitionUnavailable() {
        _uiState.update { it.copy(sttState = SttState.Error(SttErrorReason.NotAvailable)) }
        hapticService.error()
    }

    fun toggleHelp() {
        _uiState.update { it.copy(helpExpanded = !it.helpExpanded) }
    }

    private fun handleSttResult(result: SttResult) {
        when (result) {
            is SttResult.Partial -> {
                _uiState.update { it.copy(partialText = result.text) }
            }

            is SttResult.Final -> {
                val command = commandParser.parse(result.text, appLanguage)
                _uiState.update {
                    it.copy(
                        finalText = result.text,
                        partialText = "",
                        lastCommand = command
                    )
                }
                handleParsedCommand(command)
            }

            is SttResult.Error -> {
                // Silent on error: only update UI (Status text handles it via
                // live region) and rumble. Speaking an error message here would
                // race audio focus with the next StartListening call.
                _uiState.update { it.copy(partialText = "") }
                hapticService.error()
            }
        }
    }

    private fun handleParsedCommand(command: VoiceCommand) {
        viewModelScope.launch {
            // Persist the command so feature screens can pick it up.
            dataStoreManager.setLastVoiceCommand(command)

            when (command) {
                VoiceCommand.ReadText -> {
                    speakAndRemember(voiceText.readText)
                    navigateAfterSpeech(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.DescribeScene -> {
                    speakAndRemember(voiceText.describeScene)
                    navigateAfterSpeech(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.RecognizeCurrency -> {
                    speakAndRemember(voiceText.recognizeCurrency)
                    navigateAfterSpeech(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.Repeat -> {
                    if (lastSpokenText.isBlank()) {
                        speechOutput.speakAndAwait(voiceText.nothingToRepeat, appLanguage.ttsLocale)
                    } else {
                        speechOutput.speakAndAwait(lastSpokenText, appLanguage.ttsLocale)
                    }
                    restartListeningAfterSpeech()
                }

                VoiceCommand.Stop -> {
                    speechOutput.stop()
                    speakAndRemember(voiceText.stopped)
                    navigateAfterSpeech(VoiceNavigationTarget.Home)
                }

                VoiceCommand.Help -> {
                    _uiState.update { it.copy(helpExpanded = true) }
                    speakAndRemember(voiceText.help)
                    restartListeningAfterSpeech()
                }

                is VoiceCommand.Unknown -> {
                    speakAndRemember(voiceText.unknown)
                    restartListeningAfterSpeech()
                }
            }
        }
    }

    private suspend fun navigateAfterSpeech(target: VoiceNavigationTarget) {
        _navigation.trySend(target)
    }

    private fun restartListeningAfterSpeech() {
        sttService.startListening(appLanguage)
    }

    /**
     * Speak [text] and remember it for the Repeat command.
     */
    private val voiceText: VoiceText
        get() = VoiceText(
            readText = localizedTextProvider.getString(R.string.voice_vm_read_text_ack, appLanguage),
            describeScene = localizedTextProvider.getString(R.string.voice_vm_describe_scene_ack, appLanguage),
            recognizeCurrency = localizedTextProvider.getString(R.string.voice_vm_recognize_currency_ack, appLanguage),
            nothingToRepeat = localizedTextProvider.getString(R.string.voice_vm_nothing_to_repeat, appLanguage),
            stopped = localizedTextProvider.getString(R.string.voice_vm_stopped_ack, appLanguage),
            help = localizedTextProvider.getString(R.string.voice_vm_help_text, appLanguage),
            unknown = localizedTextProvider.getString(R.string.voice_vm_unknown_command, appLanguage)
        )

    private suspend fun speakAndRemember(text: String) {
        lastSpokenText = text
        speechOutput.speakAndAwait(text, appLanguage.ttsLocale)
    }

    override fun onCleared() {
        super.onCleared()
        sttService.cancel()
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
