package com.example.eyes.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.voice.CommandParser
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.system.SttErrorReason
import com.example.eyes.system.SttResult
import com.example.eyes.system.SttService
import com.example.eyes.system.SttState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    data object Map : VoiceNavigationTarget
    data object Home : VoiceNavigationTarget   // for Stop command
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
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceCommandUiState())
    val uiState: StateFlow<VoiceCommandUiState> = _uiState.asStateFlow()

    // One-shot navigation events — collected by the screen and consumed.
    private val _navigation = Channel<VoiceNavigationTarget>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    /** Text of the last response we spoke, used by the Repeat command. */
    private var lastSpokenText: String = ""

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
    }

    /**
     * Called when the screen is first composed. Does NOT speak a greeting —
     * doing so racing audio focus with the SpeechRecognizer and gets the
     * greeting silently dropped on most devices. The on-screen status text
     * and a haptic confirm convey "we're listening".
     */
    fun onScreenShown() {
        startListening()
    }

    fun startListening() {
        hapticService.confirm()
        _uiState.update { it.copy(partialText = "", finalText = "", lastCommand = null) }
        sttService.startListening()
    }

    fun stopListening() {
        sttService.stopListening()
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
                val command = commandParser.parse(result.text)
                _uiState.update {
                    it.copy(
                        finalText = result.text,
                        partialText = "",
                        lastCommand = command
                    )
                }
                handleParsedCommand(result.text, command)
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

    private fun handleParsedCommand(rawText: String, command: VoiceCommand) {
        viewModelScope.launch {
            // Persist the command so feature screens can pick it up.
            dataStoreManager.setLastVoiceCommand(command)

            when (command) {
                VoiceCommand.ReadText -> {
                    speakAndRemember("Mở chế độ đọc văn bản.")
                    delayThenNavigate(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.DescribeScene -> {
                    speakAndRemember("Mở chế độ mô tả khung cảnh.")
                    delayThenNavigate(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.RecognizeCurrency -> {
                    speakAndRemember("Mở chế độ nhận diện tiền.")
                    delayThenNavigate(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.DetectObstacle -> {
                    speakAndRemember("Mở chế độ phát hiện vật cản.")
                    delayThenNavigate(VoiceNavigationTarget.Camera)
                }

                is VoiceCommand.Navigate -> {
                    speakAndRemember("Chuẩn bị dẫn đường đến ${command.destination}.")
                    delayThenNavigate(VoiceNavigationTarget.Map)
                }

                VoiceCommand.Repeat -> {
                    if (lastSpokenText.isBlank()) {
                        speechOutput.speak("Chưa có câu nào để đọc lại.")
                    } else {
                        speechOutput.speak(lastSpokenText)
                    }
                    delayThenRestartListening()
                }

                VoiceCommand.Stop -> {
                    speechOutput.stop()
                    speakAndRemember("Đã dừng.")
                    delayThenNavigate(VoiceNavigationTarget.Home)
                }

                VoiceCommand.Help -> {
                    _uiState.update { it.copy(helpExpanded = true) }
                    speakAndRemember(HELP_TEXT)
                    delayThenRestartListening(longerDelay = true)
                }

                is VoiceCommand.Unknown -> {
                    speakAndRemember("Chưa nhận được lệnh. Hãy nói lại rõ hơn.")
                    delayThenRestartListening()
                }
            }
        }
    }

    /**
     * Wait for the TTS utterance to finish before navigating away.
     *
     * This is a simple delay — the proper fix is to surface
     * UtteranceProgressListener.onDone() through SpeechOutput, but that
     * requires expanding the interface. We pick a duration long enough to
     * cover the short feedback strings we speak here.
     *
     * Marked as tech debt: revisit if/when SpeechOutput gains an
     * "await done" API.
     */
    private suspend fun delayThenNavigate(target: VoiceNavigationTarget) {
        delay(POST_TTS_DELAY_MS)
        _navigation.trySend(target)
    }

    private suspend fun delayThenRestartListening(longerDelay: Boolean = false) {
        delay(if (longerDelay) HELP_TTS_DELAY_MS else POST_TTS_DELAY_MS)
        sttService.startListening()
    }

    /**
     * Speak [text] and remember it for the Repeat command.
     */
    private fun speakAndRemember(text: String) {
        lastSpokenText = text
        speechOutput.speak(text)
    }

    override fun onCleared() {
        super.onCleared()
        sttService.cancel()
    }

    private companion object {
        /**
         * How long to wait after speaking a short feedback string before
         * navigating or restarting the recognizer. Tuned for the actual
         * strings we speak here ("Mở chế độ X.", "Đã dừng.", etc.).
         */
        private const val POST_TTS_DELAY_MS = 2_000L

        /**
         * The HELP utterance is long (~10 seconds). Wait longer before
         * restarting the recognizer so the user can hear the full list.
         */
        private const val HELP_TTS_DELAY_MS = 12_000L

        private const val HELP_TEXT =
            "Bạn có thể nói: đọc giúp tôi để đọc văn bản. " +
                    "Trước mặt có gì để mô tả khung cảnh. " +
                    "Tờ tiền này bao nhiêu để nhận diện tiền. " +
                    "Có vật cản không để phát hiện vật cản. " +
                    "Đi đến tên địa điểm để dẫn đường. " +
                    "Đọc lại để nghe lại. " +
                    "Dừng để dừng và về trang chủ."
    }
}