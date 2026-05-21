package com.example.eyes.ui.camera

import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.camera.CurrencyAnalyzer
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.ui.navigation.CameraMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Immutable
data class CameraUiState(
    val title: String = "Camera đang sẵn sàng",
    val summary: String = "Giữ điện thoại ngang ngực và lia chậm để ứng dụng có thể mô tả vật cản ở phía trước.",
    val statusMessage: String = "Đang chờ khung hình tiếp theo",
    val currentMode: CameraMode = CameraMode.Navigation,
    // ── Currency fields ──────────────────────────────────────────
    val currencyDisplay: String = "",       // "50.000 ₫"
    val currencyConfidence: Float = 0f,
)

class CameraViewModel(
    private val tts: SpeechOutput,
    private val haptic: HapticService,
    private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)

    // Theo dõi tờ tiền cuối cùng đã đọc để không đọc lại
    private var lastSpokenLabel = ""

    // CurrencyAnalyzer khởi tạo lazy, chỉ tạo khi cần
    private val currencyAnalyzer by lazy {
        try {
            CurrencyAnalyzer(context) { label, confidence ->
                onCurrencyResult(label, confidence)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Lỗi tải model: ${e.localizedMessage}") }
            null
        }
    }

    // ── Xử lý kết quả từ CurrencyAnalyzer ────────────────────────

    private fun onCurrencyResult(label: String, confidence: Float) {
        if (label == CurrencyAnalyzer.EMPTY_LABEL) {
            // Không thấy tiền hoặc chưa ổn định
            val hadResult = _uiState.value.currencyDisplay.isNotEmpty()
            if (hadResult) {
                // Rút tờ tiền ra → reset
                lastSpokenLabel = ""
                currencyAnalyzer?.resetBuffer()
            }
            _uiState.update {
                it.copy(
                    currencyDisplay    = "",
                    currencyConfidence = 0f,
                    statusMessage      = "Giơ tờ tiền vào camera",
                )
            }
            return
        }

        val display = CurrencyAnalyzer.LABEL_DISPLAY[label] ?: label
        val labelVi = CurrencyAnalyzer.LABEL_VI[label] ?: label

        // Chỉ đọc khi nhận được tờ tiền MỚI (khác tờ trước)
        if (label != lastSpokenLabel) {
            lastSpokenLabel = label
            tts.speak(labelVi)
            haptic.confirm()
        }

        _uiState.update {
            it.copy(
                currencyDisplay    = display,
                currencyConfidence = confidence,
                statusMessage      = "Nhận diện: $display (${"%.0f%%".format(confidence * 100)})",
            )
        }
    }

    // ── Mode switching ────────────────────────────────────────────

    fun setMode(mode: CameraMode) {
        // Reset currency state khi rời khỏi mode Currency
        if (_uiState.value.currentMode == CameraMode.Currency && mode != CameraMode.Currency) {
            lastSpokenLabel = ""
            currencyAnalyzer?.resetBuffer()
        }

        _uiState.update { state ->
            when (mode) {
                CameraMode.Navigation -> state.copy(
                    title          = "Chế độ Xem xung quanh",
                    summary        = "Giữ điện thoại ngang ngực và lia chậm để ứng dụng có thể mô tả vật cản ở phía trước.",
                    statusMessage  = "Đang khởi động chế độ Xem...",
                    currentMode    = mode,
                    currencyDisplay    = "",
                    currencyConfidence = 0f,
                )
                CameraMode.OCR -> state.copy(
                    title          = "Chế độ Đọc văn bản",
                    summary        = "Hướng camera vào vùng có văn bản hoặc tài liệu để ứng dụng đọc to nội dung.",
                    statusMessage  = "Đang khởi động chế độ Đọc...",
                    currentMode    = mode,
                    currencyDisplay    = "",
                    currencyConfidence = 0f,
                )
                CameraMode.Currency -> state.copy(
                    title          = "Chế độ Nhận diện tiền",
                    summary        = "Đặt tờ tiền phẳng trước camera để ứng dụng nhận diện mệnh giá.",
                    statusMessage  = "Giơ tờ tiền vào camera",
                    currentMode    = mode,
                    currencyDisplay    = "",
                    currencyConfidence = 0f,
                )
            }
        }

        tts.speak("Đã chuyển sang ${_uiState.value.title}")
    }

    // ── Frame processing ──────────────────────────────────────────

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mode = _uiState.value.currentMode

        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (mode) {
                    CameraMode.Navigation -> processNavigation(imageProxy)
                    CameraMode.OCR        -> processOCR(imageProxy)
                    CameraMode.Currency   -> processCurrency(imageProxy)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Lỗi xử lý: ${e.localizedMessage}") }
                imageProxy.close()
            } finally {
                isProcessingFrame.set(false)
            }
        }
    }

    private fun processNavigation(imageProxy: ImageProxy) {
        // Placeholder — logic vật cản ở đây
        imageProxy.close()
        _uiState.update { it.copy(statusMessage = "Camera đang theo dõi lối đi") }
    }

    private fun processOCR(imageProxy: ImageProxy) {
        // Placeholder — logic OCR ở đây
        imageProxy.close()
        _uiState.update { it.copy(statusMessage = "Đang tìm văn bản...") }
    }

    private fun processCurrency(imageProxy: ImageProxy) {
        val analyzer = currencyAnalyzer
        if (analyzer == null) {
            imageProxy.close()
            _uiState.update { it.copy(statusMessage = "Lỗi: Không thể khởi tạo model") }
            return
        }
        // CurrencyAnalyzer tự đóng imageProxy bên trong
        analyzer.analyze(imageProxy)
    }

    // ── Cleanup ───────────────────────────────────────────────────

    override fun onCleared() {
        currencyAnalyzer?.close()
        super.onCleared()
    }
}
