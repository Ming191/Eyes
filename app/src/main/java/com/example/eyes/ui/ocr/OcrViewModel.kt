package com.example.eyes.ui.ocr

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.camera.FrameThrottle
import com.example.eyes.ocr.MlKitOcrEngine
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class OcrUiState {
    /** Màn hình vừa mở, chưa có frame nào xử lý. */
    object Idle : OcrUiState()

    /** Đang xử lý frame / chụp ảnh. */
    object Scanning : OcrUiState()

    /** Chế độ Realtime: hiển thị text mới nhất nhận dạng được. */
    data class RealtimeResult(val text: String) : OcrUiState()

    /**
     * Chế độ Document: user đã chụp ảnh, điều hướng từng câu bằng swipe.
     * [currentIndex] luôn trong đoạn [0, sentences.lastIndex].
     */
    data class DocumentMode(
        val sentences: List<String>,
        val currentIndex: Int = 0
    ) : OcrUiState() {
        val currentSentence: String get() = sentences.getOrElse(currentIndex) { "" }
        val hasNext: Boolean get() = currentIndex < sentences.lastIndex
        val hasPrev: Boolean get() = currentIndex > 0
    }

    /** Lỗi không thể recover tự động. */
    data class Error(val message: String) : OcrUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class OcrViewModel(
    private val ocrEngine: MlKitOcrEngine,
    private val tts: TtsService,
    private val haptic: HapticService
) : ViewModel() {

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val _lastRealtimeResult = MutableStateFlow(OcrResult.EMPTY)
    val lastRealtimeResult: StateFlow<OcrResult> = _lastRealtimeResult.asStateFlow()

    // Throttle riêng cho OCR: tối đa 1 frame mỗi 2 giây
    // (khác với FrameThrottle 200ms của CameraScreen dùng cho obstacle detection)
    private val ocrThrottle = FrameThrottle(intervalMs = 2_000L)

    // Text đã đọc gần nhất — dùng để so sánh similarity, tránh đọc lại
    private var lastSpokenText = ""

    // ── Realtime mode ──────────────────────────────────────────────────────────

    /**
     * Được gọi từ CameraX ImageAnalysis callback (có thể từ background thread).
     * Tự động throttle 1fps và bỏ qua khi đang ở DocumentMode.
     *
     * AI inference chạy trên [Dispatchers.Default] — không bao giờ block Main thread.
     */
    fun processFrame(imageProxy: ImageProxy) {
        // Throttle: 1 frame mỗi 2 giây
        if (!ocrThrottle.shouldProcess(System.currentTimeMillis())) {
            imageProxy.close()
            return
        }

        // Không process khi đang ở Document mode — user đang đọc
        if (_uiState.value is OcrUiState.DocumentMode) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            runCatching { ocrEngine.recognize(imageProxy) }
                .onSuccess { result ->
                    handleRealtimeResult(result)
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        haptic.error()
                        // Không update state về Error — realtime failure là expected (blur, tối, v.v.)
                    }
                }
        }
    }

    fun processCapturedImage(imageProxy: ImageProxy) {
        _uiState.value = OcrUiState.Scanning
        viewModelScope.launch(Dispatchers.Default) {
            try {
                runCatching { ocrEngine.recognize(imageProxy) }
                    .onSuccess { result ->
                        withContext(Dispatchers.Main) {
                            enterDocumentMode(result)
                        }
                    }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            _uiState.value = OcrUiState.Error("Không thể đọc ảnh đã chụp")
                            haptic.error()
                        }
                    }
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun handleRealtimeResult(result: OcrResult) {
        if (result.isEmpty) return
        _lastRealtimeResult.value = result
        val similarity = OcrPostProcessor.similarityRatio(result.fullText, lastSpokenText)
        if (similarity < 0.7f) {
            lastSpokenText = result.fullText
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.value = OcrUiState.RealtimeResult(result.fullText)
                haptic.confirm()
                tts.speak(result.fullText, TtsService.Priority.NORMAL)
            }
        }
    }

    private fun preprocessForOcr(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val scaled = scaleBitmapForOcr(bitmap)
        return if (scaled != bitmap) scaled else bitmap
    }

    private fun scaleBitmapForOcr(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val maxDimension = 1600
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val scale = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    // ── Document mode ──────────────────────────────────────────────────────────

    /**
     * Chuyển sang Document mode sau khi chụp ảnh full-res.
     * Nếu [result] rỗng → thông báo lỗi và giữ nguyên Realtime mode.
     */
    fun enterDocumentMode(result: OcrResult) {
        if (result.isEmpty) {
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
        _uiState.value = if (lastRealtimeResult.value.isEmpty) {
            OcrUiState.Idle
        } else {
            OcrUiState.RealtimeResult(lastRealtimeResult.value.fullText)
        }
        lastSpokenText = ""
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

    override fun onCleared() {
        super.onCleared()
        ocrEngine.close()
    }
}
