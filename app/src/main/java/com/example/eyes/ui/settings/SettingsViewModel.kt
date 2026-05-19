package com.example.eyes.ui.settings

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the Settings screen.
 *
 * The first three fields back the existing audio/haptic feedback sliders.
 * The remaining fields drive the "Voice test (dev)" card used to verify the
 * SttService + CommandParser pipeline end-to-end on a real device.
 */
data class SettingsUiState(
    val ttsSpeed: Float = 1.0f,
    val alertSensitivity: Float = 0.5f,
    val autoTranslateEnglishOcrToVietnamese: Boolean = false,

    // Voice test (dev only)
    val sttState: SttState = SttState.Idle,
    val partialText: String = "",
    val finalText: String = "",
    val parsedCommand: VoiceCommand? = null
)

class SettingsViewModel(
    private val dataStoreManager: DataStoreManager,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticService,
    private val sttService: SttService,
    private val commandParser: CommandParser
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStoreManager.ttsSpeedFlow,
        dataStoreManager.alertSensitivityFlow,
        dataStoreManager.ocrTranslateToVietnameseFlow,
        sttService.state
    ) { ttsSpeed, alertSensitivity, autoTranslate, sttState ->
        // Reset transient voice fields whenever the recognizer goes Idle so
        // the previous result doesn't linger after the next "start" tap.
        val reset = sttState is SttState.Idle
        SettingsUiState(
            ttsSpeed = ttsSpeed,
            alertSensitivity = alertSensitivity,
            autoTranslateEnglishOcrToVietnamese = autoTranslate,
            sttState = sttState,
            partialText = if (reset) "" else lastPartialText,
            finalText = if (reset) "" else lastFinalText,
            parsedCommand = if (reset) null else lastParsedCommand
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    // Tracked separately so that the combine() above can re-emit a fresh
    // UiState without us having to thread these through the recognizer flow.
    @Volatile private var lastPartialText: String = ""
    @Volatile private var lastFinalText: String = ""
    @Volatile private var lastParsedCommand: VoiceCommand? = null

    init {
        viewModelScope.launch {
            sttService.results.collect { result ->
                handleSttResult(result)
            }
        }
    }

    fun setTtsSpeed(value: Float) {
        viewModelScope.launch {
            speechOutput.setSpeechRate(value)
            dataStoreManager.setTtsSpeed(value)
        }
    }

    fun setAlertSensitivity(value: Float) {
        viewModelScope.launch {
            dataStoreManager.setAlertSensitivity(value)
        }
    }

    fun setAutoTranslateEnglishOcrToVietnamese(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setOcrTranslateToVietnamese(enabled)
        }
    }

    fun previewFeedback(state: SettingsUiState) {
        val speedLabel = String.format("%.2f", state.ttsSpeed)
        val sensitivityLabel = (state.alertSensitivity * 100).toInt()
        speechOutput.setSpeechRate(state.ttsSpeed)
        speechOutput.speak(
            "Đang phát thử phản hồi. Tốc độ đọc $speedLabel lần. " +
                    "Độ nhạy cảnh báo $sensitivityLabel phần trăm."
        )
        hapticService.confirm()
    }

    // ----- Voice test (dev) -----

    fun startVoiceTest() {
        // Clear previous result so the UI doesn't show stale text while the
        // recognizer warms up.
        lastPartialText = ""
        lastFinalText = ""
        lastParsedCommand = null
        hapticService.confirm()
        sttService.startListening()
    }

    fun stopVoiceTest() {
        sttService.stopListening()
    }

    fun cancelVoiceTest() {
        sttService.cancel()
    }

    fun resetVoiceTest() {
        lastPartialText = ""
        lastFinalText = ""
        lastParsedCommand = null
        sttService.reset()
    }

    private fun handleSttResult(result: SttResult) {
        when (result) {
            is SttResult.Partial -> {
                lastPartialText = result.text
            }

            is SttResult.Final -> {
                lastFinalText = result.text
                lastPartialText = ""
                val command = commandParser.parse(result.text)
                lastParsedCommand = command
                speakCommandFeedback(result.text, command)
            }

            is SttResult.Error -> {
                lastPartialText = ""
                hapticService.error()
                speechOutput.speak(errorMessageFor(result.reason))
            }
        }
    }

    private fun speakCommandFeedback(rawText: String, command: VoiceCommand) {
        val label = describeCommand(command)
        speechOutput.speak("Đã nhận: $rawText. $label")
    }

    private fun describeCommand(command: VoiceCommand): String = when (command) {
        VoiceCommand.ReadText -> "Lệnh đọc văn bản."
        VoiceCommand.DescribeScene -> "Lệnh mô tả khung cảnh."
        VoiceCommand.RecognizeCurrency -> "Lệnh nhận diện tiền."
        VoiceCommand.DetectObstacle -> "Lệnh phát hiện vật cản."
        is VoiceCommand.Navigate -> "Lệnh dẫn đường đến ${command.destination}."
        VoiceCommand.Repeat -> "Lệnh đọc lại."
        VoiceCommand.Stop -> "Lệnh dừng."
        VoiceCommand.Help -> "Lệnh trợ giúp."
        is VoiceCommand.Unknown -> "Chưa nhận diện được lệnh."
    }

    private fun errorMessageFor(reason: SttErrorReason): String = when (reason) {
        SttErrorReason.Network -> "Lỗi mạng. Hãy kiểm tra kết nối."
        SttErrorReason.NoMatch -> "Không nghe thấy gì. Hãy thử lại."
        SttErrorReason.Audio -> "Lỗi micro. Hãy thử lại."
        SttErrorReason.PermissionDenied -> "Chưa cấp quyền micro."
        SttErrorReason.NotAvailable -> "Thiết bị không hỗ trợ nhận diện giọng nói."
        is SttErrorReason.Unknown -> "Đã xảy ra lỗi không xác định."
    }

    override fun onCleared() {
        super.onCleared()
        // Stop listening if the user leaves the screen mid-recognition.
        sttService.cancel()
    }
}
