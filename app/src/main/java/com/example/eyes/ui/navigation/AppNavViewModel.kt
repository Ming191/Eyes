package com.example.eyes.ui.navigation

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.data.DataStoreManager
import com.example.eyes.R
import com.example.eyes.i18n.AndroidLocalizedTextProvider
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.ocr.OcrMode
import com.example.eyes.system.SpeechOutput
import com.example.eyes.ui.camera.CameraMode
import com.example.eyes.voiceguide.AnnouncementCategory
import com.example.eyes.voiceguide.AnnouncementController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppNavUiState(
    val isLoading: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.VI
)

class AppNavViewModel(
    private val dataStoreManager: DataStoreManager,
    private val speechOutput: SpeechOutput,
    private val announcementController: AnnouncementController,
    private val localizedTextProvider: LocalizedTextProvider
) : ViewModel() {
    constructor(
        dataStoreManager: DataStoreManager,
        speechOutput: SpeechOutput,
        announcementController: AnnouncementController,
        context: Context
    ) : this(
        dataStoreManager = dataStoreManager,
        speechOutput = speechOutput,
        announcementController = announcementController,
        localizedTextProvider = AndroidLocalizedTextProvider(context)
    )

    private val _requestedCameraMode = MutableStateFlow<CameraMode?>(null)
    val requestedCameraMode: StateFlow<CameraMode?> = _requestedCameraMode.asStateFlow()
    val currentSpokenText = speechOutput.currentSpokenText
    val uiState: StateFlow<AppNavUiState> = combine(
        dataStoreManager.onboardingCompletedFlow,
        dataStoreManager.appLanguageFlow
    ) { completed, appLanguage ->
            AppNavUiState(
                isLoading = false,
                onboardingCompleted = completed,
                appLanguage = appLanguage
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppNavUiState()
        )

    init {
        viewModelScope.launch {
            dataStoreManager.ttsSpeedFlow.collect { speed ->
                speechOutput.setSpeechRate(speed)
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            dataStoreManager.setOnboardingCompleted(true)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            dataStoreManager.setAppLanguage(language)
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

    fun announceScreen(destination: TopLevelDestination, appLanguage: AppLanguage) {
        val textRes = when (destination) {
            TopLevelDestination.HOME -> R.string.voice_guide_home_intro
            TopLevelDestination.CAMERA -> R.string.voice_guide_camera_intro
            TopLevelDestination.SETTINGS -> R.string.voice_guide_settings_intro
        }
        announcementController.announce(
            text = localizedTextProvider.getString(textRes, appLanguage),
            priority = SpeechOutput.Priority.HIGH,
            category = AnnouncementCategory.Navigation,
            locale = appLanguage.ttsLocale,
            interruptCurrent = true
        )
    }
}
