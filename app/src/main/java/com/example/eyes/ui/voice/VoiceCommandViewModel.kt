package com.example.eyes.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.application.ports.HapticFeedback
import com.example.eyes.application.ports.SettingsRepository
import com.example.eyes.application.ports.SpeechRecognitionPort
import com.example.eyes.application.voice.HandleVoiceCommandUseCase
import com.example.eyes.application.voice.SemanticVoiceCommandMatcher
import com.example.eyes.application.voice.VoiceCommandAction
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.voice.CommandParser
import com.example.eyes.domain.voice.VoiceIntent
import com.example.eyes.domain.voice.SttErrorReason
import com.example.eyes.domain.voice.SttResult
import com.example.eyes.domain.voice.SttState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoiceCommandUiState(
    val sttState: SttState = SttState.Idle,
    val partialText: String = "",
    val finalText: String = "",
    val lastIntent: VoiceIntent? = null,
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

    private val _actions = Channel<VoiceCommandAction>(Channel.BUFFERED)
    val actions = _actions.receiveAsFlow()

    private var appLanguage: AppLanguage = AppLanguage.VI
    private var isAppLanguageLoaded = false
    private var pendingStartListening = false

    init {
        viewModelScope.launch {
            speechRecognition.state.collect { sttState ->
                _uiState.update { it.copy(sttState = sttState) }
            }
        }

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
        _uiState.update { it.copy(partialText = "", finalText = "", lastIntent = null) }
        speechRecognition.startListening(appLanguage)
    }

    fun handleRecognizedText(text: String) {
        viewModelScope.launch {
            handleFinalText(text)
        }
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
                handleRecognizedText(result.text)
            }

            is SttResult.Error -> {
                _uiState.update { it.copy(partialText = "") }
                hapticService.error()
            }
        }
    }

    private suspend fun handleFinalText(text: String) {
        val language = appLanguage
        val intent = withContext(Dispatchers.Default) {
            resolveIntent(text, language)
        }
        _uiState.update {
            it.copy(
                sttState = SttState.Idle,
                partialText = "",
                finalText = text,
                lastIntent = intent
            )
        }
        handleCommandAction(handleVoiceCommand(intent, language))
    }

    private fun handleCommandAction(action: VoiceCommandAction) {
        if (action.shouldExpandHelp) {
            _uiState.update { it.copy(helpExpanded = true) }
        }
        if (action.hasExternalEffect()) {
            _actions.trySend(action)
        }
        if (action.shouldRestartListening) {
            restartListeningAfterSpeech()
        }
    }

    private fun VoiceCommandAction.hasExternalEffect(): Boolean =
        navigationTarget != null || dialNumber != null

    private fun restartListeningAfterSpeech() {
        speechRecognition.startListening(appLanguage)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognition.cancel()
    }

    private fun resolveIntent(text: String, language: AppLanguage): VoiceIntent {
        val keywordIntent = commandParser.parse(text, language)
        if (keywordIntent !is VoiceIntent.Unknown) return keywordIntent
        return semanticVoiceCommandMatcher.match(text, language) ?: keywordIntent
    }
}
