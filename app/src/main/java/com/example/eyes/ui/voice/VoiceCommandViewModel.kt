package com.example.eyes.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.application.voice.HandleVoiceCommandUseCase
import com.example.eyes.application.voice.SemanticVoiceCommandMatcher
import com.example.eyes.application.voice.VoiceCommandAction
import com.example.eyes.application.voice.VoiceNavigationTargetKind
import com.example.eyes.application.ports.HapticFeedback
import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.domain.voice.CommandParser
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.application.ports.SpeechRecognitionPort
import com.example.eyes.domain.voice.SttErrorReason
import com.example.eyes.domain.voice.SttResult
import com.example.eyes.domain.voice.SttState
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
    data object Home : VoiceNavigationTarget
    data object Settings : VoiceNavigationTarget
    data object Emergency : VoiceNavigationTarget
}

data class VoiceCommandUiState(
    val sttState: SttState = SttState.Idle,
    val partialText: String = "",
    val finalText: String = "",
    val lastCommand: VoiceCommand? = null,
    val helpExpanded: Boolean = false
)

class VoiceCommandViewModel(
    private val speechRecognition: SpeechRecognitionPort,
    private val commandParser: CommandParser,
    private val hapticService: HapticFeedback,
    private val settingsRepository: SettingsRepository,
    private val handleVoiceCommand: HandleVoiceCommandUseCase,
    private val semanticVoiceCommandMatcher: SemanticVoiceCommandMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceCommandUiState())
    val uiState: StateFlow<VoiceCommandUiState> = _uiState.asStateFlow()

    // One-shot navigation events - collected by the screen and consumed.
    private val _navigation = Channel<VoiceNavigationTarget>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    private var appLanguage: AppLanguage = AppLanguage.VI
    private var isAppLanguageLoaded = false
    private var pendingStartListening = false

    init {
        // Mirror recognizer state into UI state.
        viewModelScope.launch {
            speechRecognition.state.collect { sttState ->
                _uiState.update { it.copy(sttState = sttState) }
            }
        }

        // React to recognizer results.
        viewModelScope.launch {
            speechRecognition.results.collect { result ->
                handleSttResult(result)
            }
        }

        viewModelScope.launch {
            settingsRepository.appLanguageFlow.collect { language ->
                appLanguage = language
                isAppLanguageLoaded = true
                if (pendingStartListening) {
                    pendingStartListening = false
                    startListening()
                }
            }
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
        speechRecognition.startListening(appLanguage)
    }

    fun handleRecognizedText(text: String) {
        val command = resolveCommand(text)
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

    private fun handleSttResult(result: SttResult) {
        when (result) {
            is SttResult.Partial -> {
                _uiState.update { it.copy(partialText = result.text) }
            }

            is SttResult.Final -> {
                val command = resolveCommand(result.text)
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
            handleCommandAction(handleVoiceCommand(command, appLanguage))
        }
    }

    private fun handleCommandAction(action: VoiceCommandAction) {
        if (action.shouldExpandHelp) {
            _uiState.update { it.copy(helpExpanded = true) }
        }
        action.navigationTarget?.let { target ->
            navigateAfterSpeech(target.toUiTarget())
        }
        if (action.shouldRestartListening) {
            restartListeningAfterSpeech()
        }
    }

    private fun navigateAfterSpeech(target: VoiceNavigationTarget) {
        _navigation.trySend(target)
    }

    private fun restartListeningAfterSpeech() {
        speechRecognition.startListening(appLanguage)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognition.cancel()
    }

    private fun VoiceNavigationTargetKind.toUiTarget(): VoiceNavigationTarget = when (this) {
        VoiceNavigationTargetKind.Camera -> VoiceNavigationTarget.Camera
        VoiceNavigationTargetKind.Home -> VoiceNavigationTarget.Home
        VoiceNavigationTargetKind.Settings -> VoiceNavigationTarget.Settings
        VoiceNavigationTargetKind.Emergency -> VoiceNavigationTarget.Emergency
    }

    private fun resolveCommand(text: String): VoiceCommand {
        val keywordCommand = commandParser.parse(text, appLanguage)
        if (keywordCommand !is VoiceCommand.Unknown) return keywordCommand
        return semanticVoiceCommandMatcher.match(text, appLanguage) ?: keywordCommand
    }
}
