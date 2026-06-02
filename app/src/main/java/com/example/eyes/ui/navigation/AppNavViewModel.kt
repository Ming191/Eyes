package com.example.eyes.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.application.navigation.AnnounceDestinationUseCase
import com.example.eyes.application.navigation.ApplySpeechRateUseCase
import com.example.eyes.application.navigation.CompleteOnboardingUseCase
import com.example.eyes.application.navigation.ObserveAppNavStateUseCase
import com.example.eyes.application.navigation.SetCameraOcrModeUseCase
import com.example.eyes.application.navigation.UpdateAppLanguageUseCase
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.voice.VoiceCameraTarget
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider
import com.example.eyes.ui.camera.CameraLaunchRequest
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
    private val localizedTextProvider: LocalizedTextProvider,
) : ViewModel() {

    private var nextCameraLaunchRequestId = 0L
    private val _cameraLaunchRequest = MutableStateFlow<CameraLaunchRequest?>(null)
    val cameraLaunchRequest: StateFlow<CameraLaunchRequest?> = _cameraLaunchRequest.asStateFlow()
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
            val announcement = localizedTextProvider.getString(
                resId = when (language) {
                    AppLanguage.VI -> R.string.settings_language_changed_vietnamese
                    AppLanguage.EN -> R.string.settings_language_changed_english
                },
                language = language
            )
            speechOutput.speak(announcement, language.ttsLocale)
        }
    }

    fun requestOpenCamera(mode: CameraMode) {
        requestCameraLaunch(
            target = mode.toVoiceCameraTarget(),
            ocrMode = null,
            autoCapture = false
        )
    }

    fun requestOpenCameraOcr(ocrMode: OcrMode) {
        requestCameraLaunch(
            target = VoiceCameraTarget.OCR,
            ocrMode = ocrMode,
            autoCapture = false
        )
    }

    fun requestCameraLaunch(
        target: VoiceCameraTarget,
        ocrMode: OcrMode?,
        autoCapture: Boolean
    ) {
        _cameraLaunchRequest.value = CameraLaunchRequest(
            id = ++nextCameraLaunchRequestId,
            target = target,
            ocrMode = ocrMode,
            autoCapture = autoCapture
        )
        if (target == VoiceCameraTarget.OCR && ocrMode != null) {
            viewModelScope.launch {
                setCameraOcrModeUseCase(ocrMode)
            }
        }
    }

    fun clearCameraLaunchRequest() {
        _cameraLaunchRequest.value = null
    }

    fun announceScreen(destination: TopLevelDestination, appLanguage: AppLanguage) {
        announceDestinationUseCase(destination.toDomainDestination(), appLanguage)
    }

    private fun TopLevelDestination.toDomainDestination(): Destination = when (this) {
        TopLevelDestination.HOME -> Destination.HOME
        TopLevelDestination.CAMERA -> Destination.CAMERA
        TopLevelDestination.SETTINGS -> Destination.SETTINGS
    }

    private fun CameraMode.toVoiceCameraTarget(): VoiceCameraTarget = when (this) {
        CameraMode.OCR -> VoiceCameraTarget.OCR
        CameraMode.SCENE_DESCRIPTION -> VoiceCameraTarget.SCENE_DESCRIPTION
        CameraMode.OBJECT_DETECTION -> VoiceCameraTarget.OBJECT_DETECTION
        CameraMode.CURRENCY -> VoiceCameraTarget.CURRENCY
    }
}
