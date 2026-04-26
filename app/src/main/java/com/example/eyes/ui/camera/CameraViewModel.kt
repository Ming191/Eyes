package com.example.eyes.ui.camera

import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.camera.toBitmapWithRotation
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
    val statusMessage: String = "Đang chờ khung hình tiếp theo"
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        _uiState.update { it.copy(statusMessage = "Đang phân tích khung hình mới") }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                runCatching {
                    imageProxy.toBitmapWithRotation()
                }.onSuccess {
                    _uiState.update {
                        it.copy(statusMessage = "Camera đang theo dõi lối đi phía trước")
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(statusMessage = "Khung hình chưa rõ, tiếp tục thử lại")
                    }
                }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }
}
