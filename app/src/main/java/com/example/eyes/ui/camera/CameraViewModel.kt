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
    val currentMode: CameraMode = CameraMode.Navigation
)

class CameraViewModel(
    private val tts: SpeechOutput,
    private val haptic: HapticService,
    private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)

    private var lastSpokenCurrency = ""
    private var lastSpokenTime = 0L

    private var initializationError: String? = null
    
    // Bộ đệm để xác thực kết quả (chống đọc lung tung)
    private var recognitionBuffer = mutableListOf<String>()
    private val BUFFER_SIZE = 3 // Cần ít nhất 3 khung hình giống nhau mới đọc kết quả

    private val currencyAnalyzer by lazy {
        try {
            CurrencyAnalyzer(context) { label ->
                onCurrencyDetected(label)
            }
        } catch (e: Exception) {
            val errorMsg = "Lỗi khởi tạo AI: ${e.localizedMessage ?: "File model không hợp lệ"}"
            initializationError = errorMsg
            _uiState.update { it.copy(statusMessage = errorMsg) }
            null
        }
    }

    private fun onCurrencyDetected(label: String) {
        val currentTime = System.currentTimeMillis()
        
        if (initializationError != null) return

        // 1. Quản lý bộ đệm xác thực
        recognitionBuffer.add(label)
        if (recognitionBuffer.size > BUFFER_SIZE) {
            recognitionBuffer.removeAt(0)
        }

        // 2. Chỉ xử lý nếu kết quả ổn định và không phải nhãn trống
        val isConsistent = recognitionBuffer.size == BUFFER_SIZE && 
                          recognitionBuffer.all { it == label } && 
                          label != "000000"

        if (!isConsistent) {
            if (label == "000000") {
                _uiState.update { it.copy(statusMessage = "Chế độ tiền: Đang quét...") }
            }
            return
        }

        // Nếu là nhãn lạ chưa định nghĩa
        val speechText = when (label) {
            "000200" -> "Hai trăm đồng"
            "000500" -> "Năm trăm đồng"
            "001000" -> "Một nghìn đồng"
            "002000" -> "Hai nghìn đồng"
            "005000" -> "Năm nghìn đồng"
            "010000" -> "Mười nghìn đồng"
            "020000" -> "Hai mươi nghìn đồng"
            "050000" -> "Năm mươi nghìn đồng"
            "100000" -> "Một trăm nghìn đồng"
            "200000" -> "Hai trăm nghìn đồng"
            "500000" -> "Năm trăm nghìn đồng"
            else -> return
        }

        // Chỉ đọc mệnh giá nếu khác lần trước hoặc đã qua 3 giây
        if (label != lastSpokenCurrency || (currentTime - lastSpokenTime > 3000)) {
            lastSpokenCurrency = label
            lastSpokenTime = currentTime
            
            tts.speak(speechText)
            haptic.confirm()
            _uiState.update { it.copy(statusMessage = "Xác nhận: $speechText") }
        }
    }

    fun setMode(mode: CameraMode) {
        _uiState.update { state ->
            when (mode) {
                CameraMode.Navigation -> state.copy(
                    title = "Chế độ Xem xung quanh",
                    summary = "Giữ điện thoại ngang ngực và lia chậm để ứng dụng có thể mô tả vật cản ở phía trước.",
                    statusMessage = "Đang khởi động chế độ Xem...",
                    currentMode = mode
                )
                CameraMode.OCR -> state.copy(
                    title = "Chế độ Đọc văn bản",
                    summary = "Hướng camera vào vùng có văn bản hoặc tài liệu để ứng dụng đọc to nội dung.",
                    statusMessage = "Đang khởi động chế độ Đọc...",
                    currentMode = mode
                )
                CameraMode.Currency -> state.copy(
                    title = "Chế độ Nhận diện tiền",
                    summary = "Đặt tờ tiền phẳng trước camera để ứng dụng nhận diện mệnh giá.",
                    statusMessage = "Đang khởi động nhận diện tiền...",
                    currentMode = mode
                )
            }
        }
        
        val newTitle = _uiState.value.title
        tts.speak("Đã chuyển sang $newTitle")
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mode = _uiState.value.currentMode
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (mode) {
                    CameraMode.Navigation -> {
                        processNavigation(imageProxy)
                    }
                    CameraMode.OCR -> {
                        processOCR(imageProxy)
                    }
                    CameraMode.Currency -> {
                        val error = initializationError
                        if (error != null) {
                            _uiState.update { it.copy(statusMessage = error) }
                        } else {
                            val analyzer = currencyAnalyzer
                            if (analyzer == null) {
                                _uiState.update { it.copy(statusMessage = "Lỗi: Không thể khởi tạo bộ phân tích") }
                            } else {
                                analyzer.analyze(imageProxy)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Lỗi xử lý khung hình: ${e.localizedMessage}") }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    private fun processNavigation(imageProxy: ImageProxy) {
        // Placeholder for navigation logic
        _uiState.update { it.copy(statusMessage = "Camera đang theo dõi lối đi") }
    }

    private fun processOCR(imageProxy: ImageProxy) {
        // Placeholder for OCR logic
        _uiState.update { it.copy(statusMessage = "Đang tìm văn bản...") }
    }
}
