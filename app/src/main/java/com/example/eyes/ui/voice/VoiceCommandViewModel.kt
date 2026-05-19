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
     * Called when the screen is first composed. Greets the user and starts
     * listening so blind users don't have to find a button before speaking.
     */
    fun onScreenShown() {
        speakAndRemember("Đang lắng nghe lệnh. Hãy nói.")
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
                _uiState.update { it.copy(partialText = "") }
                hapticService.error()
                val message = errorMessageFor(result.reason)
                speakAndRemember(message)
                // After error, the user will likely want to try again.
                // We don't auto-restart here — the screen exposes a Retry button.
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
                    _navigation.trySend(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.DescribeScene -> {
                    speakAndRemember("Mở chế độ mô tả khung cảnh.")
                    _navigation.trySend(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.RecognizeCurrency -> {
                    speakAndRemember("Mở chế độ nhận diện tiền.")
                    _navigation.trySend(VoiceNavigationTarget.Camera)
                }

                VoiceCommand.DetectObstacle -> {
                    speakAndRemember("Mở chế độ phát hiện vật cản.")
                    _navigation.trySend(VoiceNavigationTarget.Camera)
                }

                is VoiceCommand.Navigate -> {
                    speakAndRemember("Chuẩn bị dẫn đường đến ${command.destination}.")
                    _navigation.trySend(VoiceNavigationTarget.Map)
                }

                VoiceCommand.Repeat -> {
                    if (lastSpokenText.isBlank()) {
                        speechOutput.speak("Chưa có câu nào để đọc lại.")
                    } else {
                        speechOutput.speak(lastSpokenText)
                    }
                    // Stay on voice screen, listen again so the user can issue a new command.
                    restartListeningSoon()
                }

                VoiceCommand.Stop -> {
                    speechOutput.stop()
                    speakAndRemember("Đã dừng.")
                    _navigation.trySend(VoiceNavigationTarget.Home)
                }

                VoiceCommand.Help -> {
                    _uiState.update { it.copy(helpExpanded = true) }
                    speakAndRemember(HELP_TEXT)
                    restartListeningSoon()
                }

                is VoiceCommand.Unknown -> {
                    speakAndRemember("Chưa nhận được lệnh. Hãy nói lại rõ hơn.")
                    restartListeningSoon()
                }
            }
        }
    }

    /**
     * Speak [text] and remember it for the Repeat command.
     * Uses the TTS engine's NORMAL priority — voice screen does not produce
     * safety-critical announcements.
     */
    private fun speakAndRemember(text: String) {
        lastSpokenText = text
        speechOutput.speak(text)
    }

    /**
     * After spoken feedback ends we want to start listening again so the user
     * can keep talking without tapping. We don't have a TTS-done callback in
     * the SpeechOutput abstraction, so we kick off recognition immediately —
     * SpeechRecognizer queues internally and will start after audio focus
     * returns from the TTS utterance.
     */
    private fun restartListeningSoon() {
        sttService.startListening()
    }

    private fun errorMessageFor(reason: SttErrorReason): String = when (reason) {
        SttErrorReason.Network -> "Lỗi mạng. Hãy kiểm tra kết nối rồi thử lại."
        SttErrorReason.NoMatch -> "Không nghe thấy gì. Hãy bấm nút và thử lại."
        SttErrorReason.Audio -> "Lỗi micro. Hãy thử lại."
        SttErrorReason.PermissionDenied -> "Chưa cấp quyền micro. Hãy vào cài đặt để bật quyền."
        SttErrorReason.NotAvailable -> "Thiết bị không hỗ trợ nhận diện giọng nói."
        is SttErrorReason.Unknown -> "Đã xảy ra lỗi không xác định. Hãy thử lại."
    }

    override fun onCleared() {
        super.onCleared()
        sttService.cancel()
    }

    private companion object {
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