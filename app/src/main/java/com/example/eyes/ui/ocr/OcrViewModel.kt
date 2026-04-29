package com.example.eyes.ui.ocr

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.camera.toBitmapWithRotation
import com.example.eyes.data.DataStoreManager
import com.example.eyes.ocr.OcrEngine
import com.example.eyes.ocr.OcrLanguage
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult
import com.example.eyes.ocr.OcrTranslator
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class OcrUiState {
    data object Idle : OcrUiState()
    data object Scanning : OcrUiState()
    data class DocumentMode(
        val sentences: List<String>,
        val currentIndex: Int = 0
    ) : OcrUiState() {
        val currentSentence: String get() = sentences.getOrElse(currentIndex) { "" }
        val hasNext: Boolean get() = currentIndex < sentences.lastIndex
        val hasPrev: Boolean get() = currentIndex > 0
    }
    data class Error(val message: String) : OcrUiState()
}

class OcrViewModel(
    private val quickOcrEngine: OcrEngine,
    private val accuracyOcrEngine: OcrEngine,
    private val translator: OcrTranslator,
    private val dataStoreManager: DataStoreManager,
    private val tts: TtsService,
    private val haptic: HapticService
) : ViewModel() {

    private companion object {
        private const val TAG = "OcrViewModel"
        private val VI_DIACRITIC_REGEX = Regex(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            RegexOption.IGNORE_CASE
        )
    }

    internal data class CaptureRecognitionOutcome(
        val result: Result<OcrResult>,
        val usedFallbackFromAccuracy: Boolean,
        val primaryError: Throwable? = null
    )

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    val ocrMode: StateFlow<OcrMode> = dataStoreManager.ocrModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OcrMode.QUICK
        )

    val ocrLanguage: StateFlow<OcrLanguage> = dataStoreManager.ocrLanguageFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OcrLanguage.AUTO
        )

    val ocrTranslateToVietnamese: StateFlow<Boolean> = dataStoreManager.ocrTranslateToVietnameseFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setOcrMode(mode: OcrMode) {
        viewModelScope.launch {
            dataStoreManager.setOcrMode(mode)
            if (_uiState.value !is OcrUiState.DocumentMode) {
                _uiState.value = OcrUiState.Idle
            }
            tts.speak(
                when (mode) {
                    OcrMode.QUICK -> "Đã chuyển sang chế độ nhanh."
                    OcrMode.ACCURACY -> "Đã chuyển sang chế độ chính xác. Ảnh sẽ được gửi lên cloud để nhận dạng tốt hơn."
                },
                TtsService.Priority.NORMAL
            )
        }
    }

    fun setOcrLanguage(language: OcrLanguage) {
        viewModelScope.launch {
            dataStoreManager.setOcrLanguage(language)
            val announcement = when (language) {
                OcrLanguage.AUTO -> "Đã chọn ngôn ngữ OCR: tự động."
                OcrLanguage.VI -> "Đã chọn ngôn ngữ OCR: tiếng Việt."
                OcrLanguage.EN -> "Đã chọn ngôn ngữ OCR: tiếng Anh."
            }
            tts.speak(announcement, TtsService.Priority.NORMAL)
        }
    }

    fun setTranslateToVietnamese(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setOcrTranslateToVietnamese(enabled)
            val announcement = if (enabled) {
                "Đã bật dịch sang tiếng Việt."
            } else {
                "Đã tắt dịch sang tiếng Việt."
            }
            tts.speak(announcement, TtsService.Priority.NORMAL)
        }
    }

    fun processCapturedImage(imageProxy: ImageProxy) {
        _uiState.value = OcrUiState.Scanning
        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = try {
                imageProxy.toBitmapWithRotation()
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    _uiState.value = OcrUiState.Error("Không thể xử lý ảnh đã chụp")
                    haptic.error()
                }
                return@launch
            } finally {
                imageProxy.close()
            }

            val mode = ocrMode.value
            val outcome = recognizeCapturedBitmap(bitmap = bitmap, mode = mode)

            if (outcome.usedFallbackFromAccuracy) {
                withContext(Dispatchers.Main) {
                    val detailMessage = outcome.primaryError?.let { buildAccuracyFallbackErrorMessage(it) }
                        ?: "Chế độ chính xác gặp lỗi. Đã chuyển sang chế độ nhanh."
                    Log.w(
                        TAG,
                        "Accuracy OCR failed; fallback to quick. " +
                            "${outcome.primaryError?.javaClass?.simpleName}: ${outcome.primaryError?.message}",
                        outcome.primaryError
                    )
                    tts.speak(detailMessage, TtsService.Priority.HIGH)
                }
            }

            outcome.result
                .onSuccess { data ->
                    val displayResult = prepareResultForReading(data)
                    withContext(Dispatchers.Main) {
                        enterDocumentMode(displayResult)
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        val reason = when {
                            outcome.usedFallbackFromAccuracy && outcome.primaryError != null -> buildAccuracyFallbackErrorMessage(outcome.primaryError)
                            error is IOException -> "Không thể đọc ảnh đã chụp. Vui lòng thử lại."
                            else -> "Không thể đọc ảnh đã chụp: ${error.message ?: "lỗi không xác định"}"
                        }
                        Log.e(
                            TAG,
                            "Captured OCR failed. mode=$mode usedFallback=${outcome.usedFallbackFromAccuracy}. $reason",
                            error
                        )
                        _uiState.value = OcrUiState.Error(reason)
                        haptic.error()
                    }
                }
        }
    }

    internal suspend fun recognizeCapturedBitmap(
        bitmap: android.graphics.Bitmap,
        mode: OcrMode
    ): CaptureRecognitionOutcome {
        return when (mode) {
            OcrMode.QUICK -> {
                CaptureRecognitionOutcome(
                    result = runCatching { quickOcrEngine.recognize(bitmap) },
                    usedFallbackFromAccuracy = false
                )
            }

            OcrMode.ACCURACY -> {
                val accuracyResult = runCatching { accuracyOcrEngine.recognize(bitmap) }
                if (accuracyResult.isSuccess) {
                    CaptureRecognitionOutcome(
                        result = accuracyResult,
                        usedFallbackFromAccuracy = false
                    )
                } else {
                    val fallbackResult = runCatching { quickOcrEngine.recognize(bitmap) }
                    CaptureRecognitionOutcome(
                        result = fallbackResult,
                        usedFallbackFromAccuracy = true,
                        primaryError = accuracyResult.exceptionOrNull()
                    )
                }
            }
        }
    }

    fun enterDocumentMode(result: OcrResult) {
        if (result.isEmpty) {
            _uiState.value = OcrUiState.Idle
            haptic.error()
            tts.speak(
                "Không phát hiện văn bản. Hãy hướng camera vào trang sách hoặc biển hiệu.",
                TtsService.Priority.HIGH
            )
            return
        }

        val documentResult = if (result.sentences.isEmpty() && result.fullText.isNotBlank()) {
            result.copy(sentences = OcrPostProcessor.splitToSentences(result.fullText))
        } else {
            result
        }

        val sentences = documentResult.sentences.ifEmpty {
            listOf(documentResult.fullText.trim()).filter { it.isNotBlank() }
        }

        if (sentences.isEmpty()) {
            _uiState.value = OcrUiState.Idle
            haptic.error()
            tts.speak(
                "Không tách được câu đọc. Hãy thử chụp rõ hơn.",
                TtsService.Priority.HIGH
            )
            return
        }

        _uiState.value = OcrUiState.DocumentMode(sentences = sentences)
        tts.speak(
            "Đã chụp. ${sentences.size} đoạn văn. Vuốt phải để nghe tiếp.",
            TtsService.Priority.HIGH
        )
        haptic.confirm()
        readCurrentSentence()
    }

    fun nextSentence() {
        val state = _uiState.value as? OcrUiState.DocumentMode ?: return
        if (!state.hasNext) {
            tts.speak("Đã đến cuối văn bản.", TtsService.Priority.HIGH)
            return
        }
        _uiState.value = state.copy(currentIndex = state.currentIndex + 1)
        readCurrentSentence()
    }

    fun prevSentence() {
        val state = _uiState.value as? OcrUiState.DocumentMode ?: return
        if (!state.hasPrev) {
            tts.speak("Đây là đầu văn bản.", TtsService.Priority.HIGH)
            return
        }
        _uiState.value = state.copy(currentIndex = state.currentIndex - 1)
        readCurrentSentence()
    }

    fun exitDocumentMode() {
        _uiState.value = OcrUiState.Idle
        tts.speak("Chế độ đọc tài liệu đã tắt.", TtsService.Priority.NORMAL)
    }

    fun onCaptureError() {
        _uiState.value = OcrUiState.Error("Không thể chụp ảnh. Hãy thử lại.")
        haptic.error()
    }

    private fun readCurrentSentence() {
        val state = _uiState.value as? OcrUiState.DocumentMode ?: return
        val position = "${state.currentIndex + 1} trên ${state.sentences.size}"
        tts.speak("$position. ${state.currentSentence}", TtsService.Priority.HIGH)
    }

    private suspend fun prepareResultForReading(result: OcrResult): OcrResult {
        val languagePref = ocrLanguage.value
        val translateEnabled = ocrTranslateToVietnamese.value
        val resolvedLanguage = resolveLanguage(result.fullText, languagePref)
        val processed = OcrPostProcessor.process(result.fullText, resolvedLanguage)

        if (!translateEnabled || resolvedLanguage != OcrLanguage.EN || processed.fullText.isBlank()) {
            return processed
        }

        return try {
            val translatedText = translator.translateToVietnamese(processed.fullText)
            OcrPostProcessor.process(translatedText, OcrLanguage.VI)
        } catch (error: Throwable) {
            Log.w(TAG, "Translation failed; falling back to original text", error)
            withContext(Dispatchers.Main) {
                tts.speak(
                    "Không thể dịch sang tiếng Việt. Đang đọc bản gốc.",
                    TtsService.Priority.NORMAL
                )
            }
            processed
        }
    }

    private fun resolveLanguage(text: String, preference: OcrLanguage): OcrLanguage {
        return when (preference) {
            OcrLanguage.AUTO -> if (VI_DIACRITIC_REGEX.containsMatchIn(text)) OcrLanguage.VI else OcrLanguage.EN
            else -> preference
        }
    }

    private fun buildAccuracyFallbackErrorMessage(error: Throwable): String {
        return when (error) {
            is java.net.SocketTimeoutException -> "Chế độ chính xác bị timeout khi gọi GPT-4o. Đã chuyển sang chế độ nhanh."
            is java.io.IOException -> {
                val message = error.message.orEmpty()
                when {
                    message.contains("401") -> "Sai API key OpenAI. Hãy kiểm tra lại OPENAI_API_KEY trong .env."
                    message.contains("403") -> "API key không có quyền truy cập model hoặc endpoint."
                    message.contains("429") -> "OpenAI đang giới hạn request hoặc hết quota."
                    message.contains("400") -> "Request gửi lên GPT-4o không hợp lệ."
                    else -> "Chế độ chính xác gặp lỗi I/O: ${message.ifBlank { "không rõ nguyên nhân" }}. Đã chuyển sang chế độ nhanh."
                }
            }
            else -> "Chế độ chính xác gặp lỗi: ${error.message ?: "không rõ nguyên nhân"}. Đã chuyển sang chế độ nhanh."
        }
    }


    override fun onCleared() {
        super.onCleared()
        quickOcrEngine.close()
        accuracyOcrEngine.close()
    }
}
