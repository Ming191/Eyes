package com.example.eyes.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.application.navigation.AnnounceDestinationUseCase
import com.example.eyes.application.navigation.ApplySpeechRateUseCase
import com.example.eyes.application.navigation.CompleteOnboardingUseCase
import com.example.eyes.application.navigation.ObserveAppNavStateUseCase
import com.example.eyes.application.navigation.SetCameraOcrModeUseCase
import com.example.eyes.application.navigation.UpdateAppLanguageUseCase
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.system.SpeechOutput
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
    val onboardingCompleted: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.VI
)

class AppNavViewModel(
    observeAppNavState: ObserveAppNavStateUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val updateAppLanguageUseCase: UpdateAppLanguageUseCase,
    private val applySpeechRateUseCase: ApplySpeechRateUseCase,
    private val setCameraOcrModeUseCase: SetCameraOcrModeUseCase,
    private val announceDestinationUseCase: AnnounceDestinationUseCase,
    private val speechOutput: SpeechOutput,
) : ViewModel() {

    private val _requestedCameraMode = MutableStateFlow<CameraMode?>(null)
    val requestedCameraMode: StateFlow<CameraMode?> = _requestedCameraMode.asStateFlow()
    val currentSpokenText = speechOutput.currentSpokenText
    val uiState: StateFlow<AppNavUiState> = observeAppNavState().map { state ->
            AppNavUiState(
                isLoading = false,
                onboardingCompleted = state.onboardingCompleted,
                appLanguage = state.appLanguage
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppNavUiState()
        )

    init {
        viewModelScope.launch {
            applySpeechRateUseCase()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            completeOnboardingUseCase()
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            updateAppLanguageUseCase(language)
        }
    }

    fun requestOpenCamera(mode: CameraMode) {
        _requestedCameraMode.value = mode
    }

    fun requestOpenCameraOcr(ocrMode: OcrMode) {
        _requestedCameraMode.value = CameraMode.OCR
        viewModelScope.launch {
            setCameraOcrModeUseCase(ocrMode)
        }
    }

    fun clearRequestedCameraMode() {
        _requestedCameraMode.value = null
    }

    fun announceScreen(destination: TopLevelDestination, appLanguage: AppLanguage) {
        announceDestinationUseCase(destination.toDomainDestination(), appLanguage)
    }

    private fun TopLevelDestination.toDomainDestination(): Destination = when (this) {
        TopLevelDestination.HOME -> Destination.HOME
        TopLevelDestination.CAMERA -> Destination.CAMERA
        TopLevelDestination.SETTINGS -> Destination.SETTINGS
    }
}
