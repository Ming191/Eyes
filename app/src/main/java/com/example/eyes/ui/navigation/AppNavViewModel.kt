package com.example.eyes.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.data.DataStoreManager
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ui.camera.CameraMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppNavUiState(
    val isLoading: Boolean = true,
    val onboardingCompleted: Boolean = false
)

class AppNavViewModel(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {
    private val _requestedCameraMode = MutableStateFlow<CameraMode?>(null)
    val requestedCameraMode: StateFlow<CameraMode?> = _requestedCameraMode.asStateFlow()

    val uiState: StateFlow<AppNavUiState> = dataStoreManager.onboardingCompletedFlow
        .map { completed ->
            AppNavUiState(
                isLoading = false,
                onboardingCompleted = completed
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppNavUiState()
        )

    fun completeOnboarding() {
        viewModelScope.launch {
            dataStoreManager.setOnboardingCompleted(true)
        }
    }

    fun requestOpenCamera(mode: CameraMode) {
        _requestedCameraMode.value = mode
    }

    fun requestOpenCameraOcr(ocrMode: OcrMode) {
        _requestedCameraMode.value = CameraMode.OCR
        viewModelScope.launch {
            dataStoreManager.setOcrMode(ocrMode)
        }
    }

    fun clearRequestedCameraMode() {
        _requestedCameraMode.value = null
    }
}
