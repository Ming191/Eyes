package com.example.eyes.ui.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.application.camera.ObserveCameraPreferencesUseCase
import com.example.eyes.application.navigation.SetCameraOcrModeUseCase
import com.example.eyes.application.objectdetection.DetectObjectsUseCase
import com.example.eyes.application.objectdetection.WarmUpObjectDetectionUseCase
import com.example.eyes.application.ocr.RecognizeOcrDocumentUseCase
import com.example.eyes.application.ports.CurrencyRecognizerFactory
import com.example.eyes.application.ports.OcrGuidanceAnalyzerPort
import com.example.eyes.application.scene.DescribeSceneUseCase
import com.example.eyes.domain.audio.AudioRouteProvider
import com.example.eyes.domain.haptics.HapticFeedback
import com.example.eyes.domain.voice.VoiceCommandRepository
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.domain.ocr.OcrGuidanceStatus
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.ocr.OcrTextBounds
import com.example.eyes.domain.speech.SpeechOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

@Immutable
data class CameraUiState(
    val activeMode: CameraMode = CameraMode.OBJECT_DETECTION,
    val ocrMode: OcrMode = OcrMode.QUICK,
    val ocrTranslateToVietnamese: Boolean = false,
    val canTranslateCurrentOcrDocument: Boolean = false,
    val isOcrScanning: Boolean = false,
    val ocrSentences: List<String> = emptyList(),
    val ocrCurrentIndex: Int = 0,
    val ocrCapturedBitmap: Bitmap? = null,
    val ocrGuidanceStatus: OcrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
    val ocrGuidanceMessage: String = "",
    val isOcrReadyToCapture: Boolean = false,
    val ocrGuidanceBounds: OcrTextBounds? = null,
    val title: String = "",
    val summary: String = "",
    val statusMessage: String = "",
    val lastAnnouncement: String = "",
    val debugMetrics: String = "",
    val isDescribingScene: Boolean = false,
    val isCurrencyScanning: Boolean = false,
    val isStatusCardVisible: Boolean = true,
    val objectDetections: List<DetectionOverlayItem> = emptyList(),
    val currencyDisplay: String = "",
    val currencyConfidence: Float = 0f
) {
    val isOcrDocumentMode: Boolean get() = ocrSentences.isNotEmpty()
    val currentOcrSentence: String get() = ocrSentences.getOrElse(ocrCurrentIndex) { "" }
    val hasNextOcrSentence: Boolean get() = ocrCurrentIndex < ocrSentences.lastIndex
    val hasPrevOcrSentence: Boolean get() = ocrCurrentIndex > 0
}

@Immutable
enum class CameraMode {
    OCR,
    SCENE_DESCRIPTION,
    OBJECT_DETECTION,
    CURRENCY
}

@Immutable
data class DetectionOverlayItem(
    val label: String,
    val confidence: Float,
    val positionText: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val sourceAspectRatio: Float
)

class CameraViewModel(
    private val recognizeOcrDocumentUseCase: RecognizeOcrDocumentUseCase,
    private val ocrGuidanceAnalyzer: OcrGuidanceAnalyzerPort,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticFeedback,
    private val observeCameraPreferences: ObserveCameraPreferencesUseCase,
    private val setCameraOcrModeUseCase: SetCameraOcrModeUseCase,
    private val voiceCommandRepository: VoiceCommandRepository,
    private val describeSceneUseCase: DescribeSceneUseCase,
    private val detectObjectsUseCase: DetectObjectsUseCase,
    private val warmUpObjectDetectionUseCase: WarmUpObjectDetectionUseCase,
    private val audioRouteProvider: AudioRouteProvider,
    private val currencyRecognizerFactory: CurrencyRecognizerFactory,
    private val imageConverter: CameraImageConverter,
    private val localizedTextProvider: LocalizedTextProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraText.from(localizedTextProvider, AppLanguage.VI).initialUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val currentOcrMode = MutableStateFlow(OcrMode.QUICK)

    private val bitmapStore = CameraBitmapStore()
    private val appLanguage = AtomicReference(AppLanguage.VI)
    private val currencyTextMapper = CurrencyTextMapper(localizedTextProvider) { appLanguage.get() }
    private val ocrDocumentController = OcrDocumentController(
        uiState = _uiState,
        speechOutput = speechOutput,
        hapticService = hapticService,
        cameraText = { cameraText },
        appLanguage = { appLanguage.get() }
    )
    private val ocrGuidanceController = OcrGuidanceController(
        uiState = _uiState,
        analyzer = ocrGuidanceAnalyzer,
        speechOutput = speechOutput,
        hapticService = hapticService,
        bitmapStore = bitmapStore,
        imageConverter = imageConverter,
        cameraText = { cameraText },
        appLanguage = { appLanguage.get() }
    )
    private val ocrCaptureController = OcrCaptureController(
        uiState = _uiState,
        currentOcrMode = currentOcrMode,
        recognizeOcrDocumentUseCase = recognizeOcrDocumentUseCase,
        setCameraOcrModeUseCase = setCameraOcrModeUseCase,
        ocrDocumentController = ocrDocumentController,
        speechOutput = speechOutput,
        hapticService = hapticService,
        imageConverter = imageConverter,
        cameraText = { cameraText },
        appLanguage = { appLanguage.get() },
        resetGuidance = ocrGuidanceController::reset,
        updateUiStateAndRecycleReplacedBitmap = ::updateUiStateAndRecycleReplacedOcrBitmap
    )
    private val sceneCaptureController = SceneCaptureController(
        uiState = _uiState,
        describeSceneUseCase = describeSceneUseCase,
        speechOutput = speechOutput,
        hapticService = hapticService,
        audioRouteProvider = audioRouteProvider,
        imageConverter = imageConverter,
        cameraText = { cameraText },
        appLanguage = { appLanguage.get() },
        updateUiStateAndRecycleReplacedBitmap = ::updateUiStateAndRecycleReplacedOcrBitmap
    )
    private val objectDetectionController = ObjectDetectionController(
        uiState = _uiState,
        detectObjectsUseCase = detectObjectsUseCase,
        warmUpObjectDetectionUseCase = warmUpObjectDetectionUseCase,
        speechOutput = speechOutput,
        bitmapStore = bitmapStore,
        imageConverter = imageConverter,
        localizedTextProvider = localizedTextProvider,
        cameraText = { cameraText },
        appLanguage = { appLanguage.get() }
    )
    private val currencyRecognitionController = CurrencyRecognitionController(
        uiState = _uiState,
        currencyRecognizerFactory = currencyRecognizerFactory,
        speechOutput = speechOutput,
        hapticService = hapticService,
        bitmapStore = bitmapStore,
        imageConverter = imageConverter,
        currencyTextMapper = currencyTextMapper,
        cameraText = { cameraText },
        appLanguage = { appLanguage.get() },
        updateUiStateAndRecycleReplacedBitmap = ::updateUiStateAndRecycleReplacedOcrBitmap
    )
    private val cameraText: CameraText get() = CameraText.from(localizedTextProvider, appLanguage.get())

    init {
        viewModelScope.launch {
            observeCameraPreferences().collect { preferences ->
                setAppLanguage(preferences.appLanguage)
                currentOcrMode.value = preferences.ocrMode
                _uiState.update {
                    it.copy(
                        ocrMode = preferences.ocrMode,
                        ocrTranslateToVietnamese = preferences.ocrTranslateToVietnamese
                    )
                }
            }
        }
        viewModelScope.launch {
            voiceCommandRepository.lastVoiceCommandFlow.collect { command ->
                when (command) {
                    VoiceCommand.ReadText -> applyVoiceCameraCommand(
                        mode = CameraMode.OCR,
                        statusMessage = cameraText.waitingForClearTextFrame
                    )

                    VoiceCommand.DescribeScene -> applyVoiceCameraCommand(
                        mode = CameraMode.SCENE_DESCRIPTION,
                        title = cameraText.sceneDescriptionTitle,
                        summary = cameraText.sceneDescriptionSummary,
                        statusMessage = cameraText.waitingForSceneFrame
                    )

                    VoiceCommand.RecognizeCurrency -> applyVoiceCameraCommand(
                        mode = CameraMode.CURRENCY,
                        title = cameraText.currencyTitle,
                        summary = cameraText.currencySummary,
                        statusMessage = cameraText.currencyInstruction
                    )

                    else -> return@collect
                }
                voiceCommandRepository.clearLastVoiceCommand()
            }
        }
        objectDetectionController.warmUp(viewModelScope)
    }

    fun setAppLanguage(language: AppLanguage) {
        val previousLanguage = appLanguage.get()
        if (previousLanguage == language) return

        val previousText = cameraText
        appLanguage.set(language)
        refreshLanguageBoundUiText(previousText, cameraText)
    }

    private fun applyVoiceCameraCommand(
        mode: CameraMode,
        title: String = cameraText.ocrTitle,
        summary: String = cameraText.ocrSummary,
        statusMessage: String
    ) {
        objectDetectionController.resetAnnouncementDebounce()
        resetCurrencyAnnouncementDebounce()
        _uiState.update {
            it
                .resetOcrRuntime(cameraText)
                .resetDetectionAndCurrency()
                .copy(
                activeMode = mode,
                title = title,
                summary = summary,
                statusMessage = statusMessage,
                lastAnnouncement = statusMessage,
                isDescribingScene = false
            )
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        when (_uiState.value.activeMode) {
            CameraMode.OCR -> processOcrGuidanceImageProxy(imageProxy)
            CameraMode.SCENE_DESCRIPTION -> imageProxy.close()
            CameraMode.OBJECT_DETECTION -> processObjectDetectionImageProxy(imageProxy)
            CameraMode.CURRENCY -> processCurrencyPreviewImageProxy(imageProxy)
        }
    }

    private fun processCurrencyPreviewImageProxy(imageProxy: ImageProxy) {
        currencyRecognitionController.processPreviewImageProxy(imageProxy, viewModelScope)
    }

    private fun processObjectDetectionImageProxy(imageProxy: ImageProxy) {
        objectDetectionController.processImageProxy(imageProxy, viewModelScope)
    }

    private fun processOcrGuidanceImageProxy(imageProxy: ImageProxy) {
        ocrGuidanceController.processImageProxy(imageProxy, viewModelScope)
    }

    private fun resetCurrencyAnnouncementDebounce() {
        currencyRecognitionController.resetAnnouncementDebounce()
    }

    fun processCapturedOcrImage(imageProxy: ImageProxy) {
        ocrCaptureController.processCapturedImage(imageProxy, viewModelScope)
    }

    fun onOcrCaptureError() {
        ocrCaptureController.onCaptureError()
    }

    fun onOcrCaptureRequested() {
        ocrCaptureController.onCaptureRequested()
    }

    fun prepareForNextOcrCapture() {
        ocrCaptureController.prepareForNextCapture()
    }

    fun nextOcrSentence() {
        ocrDocumentController.nextOcrSentence()
    }

    fun prevOcrSentence() {
        ocrDocumentController.prevOcrSentence()
    }

    fun selectMode(mode: CameraMode) {
        if (_uiState.value.activeMode == mode) return
        if (_uiState.value.activeMode == CameraMode.CURRENCY && mode != CameraMode.CURRENCY) {
            currencyRecognitionController.resetBuffer()
        }
        ocrGuidanceController.reset()
        objectDetectionController.resetAnnouncementDebounce()
        resetCurrencyAnnouncementDebounce()
        when (mode) {
            CameraMode.OCR -> {
                hapticService.confirm()
                if (!audioRouteProvider.isHeadsetConnected()) {
                    val modeLabel = cameraText.ocrModeLabel(currentOcrMode.value)
                    speechOutput.speak(cameraText.switchedToOcrMode(modeLabel), appLanguage.get().ttsLocale)
                }
                speechOutput.warmupLocale(Locale.US)
                _uiState.update {
                    it.resetOcrRuntime(cameraText)
                        .resetDetectionAndCurrency()
                        .copy(
                            activeMode = CameraMode.OCR,
                            title = cameraText.ocrTitle,
                            summary = cameraText.ocrSummary,
                            statusMessage = cameraText.readyToCaptureOcr,
                            lastAnnouncement = cameraText.switchedToReadTextMode,
                            debugMetrics = cameraText.ocrDebugWaiting,
                            isDescribingScene = false
                        )
                }
            }

            CameraMode.SCENE_DESCRIPTION -> {
                hapticService.confirm()
                if (!audioRouteProvider.isHeadsetConnected()) {
                    speechOutput.speak(
                        cameraText.switchedToSceneDescriptionMode,
                        appLanguage.get().ttsLocale
                    )
                }
                _uiState.update {
                    it.resetOcrRuntime(cameraText)
                        .resetDetectionAndCurrency()
                        .copy(
                            activeMode = CameraMode.SCENE_DESCRIPTION,
                            title = cameraText.sceneDescriptionTitle,
                            summary = cameraText.sceneDescriptionSummary,
                            statusMessage = cameraText.waitingForSceneFrame,
                            lastAnnouncement = cameraText.switchedToSceneDescriptionMode,
                            isDescribingScene = false
                        )
                }
            }

            CameraMode.OBJECT_DETECTION -> {
                hapticService.confirm()
                if (!audioRouteProvider.isHeadsetConnected()) {
                    speechOutput.speak(cameraText.switchedToObjectDetectionMode, appLanguage.get().ttsLocale)
                }
                _uiState.update {
                    it.resetOcrRuntime(cameraText)
                        .resetDetectionAndCurrency()
                        .copy(
                            activeMode = CameraMode.OBJECT_DETECTION,
                            title = cameraText.objectDetectionTitle,
                            summary = cameraText.objectDetectionSummary,
                            statusMessage = cameraText.objectDetectionStatus,
                            lastAnnouncement = cameraText.switchedToObjectDetectionMode,
                            debugMetrics = cameraText.objectDetectionDebug(0),
                            isDescribingScene = false
                        )
                }
            }

            CameraMode.CURRENCY -> {
                currencyRecognitionController.resetBuffer()
                hapticService.confirm()
                if (!audioRouteProvider.isHeadsetConnected()) {
                    speechOutput.speak(cameraText.switchedToCurrencyMode, appLanguage.get().ttsLocale)
                }
                _uiState.update {
                    it.resetOcrRuntime(cameraText)
                        .resetDetectionAndCurrency()
                        .copy(
                            activeMode = CameraMode.CURRENCY,
                            title = cameraText.currencyTitle,
                            summary = cameraText.currencySummary,
                            statusMessage = cameraText.currencyInstruction,
                            lastAnnouncement = cameraText.switchedToCurrencyMode,
                            debugMetrics = cameraText.currencyDebugWaiting,
                            isDescribingScene = false
                        )
                }
            }
        }
    }

    fun selectOcrMode(mode: OcrMode) {
        if (currentOcrMode.value == mode) return
        viewModelScope.launch {
            setCameraOcrModeUseCase(mode)
            hapticService.confirm()
            speechOutput.speak(
                cameraText.switchedOcrMode(mode),
                appLanguage.get().ttsLocale
            )
        }
    }

    fun onSceneCaptureRequested() {
        sceneCaptureController.onSceneCaptureRequested()
    }

    fun processCapturedSceneImage(imageProxy: ImageProxy) {
        viewModelScope.launch(Dispatchers.Default) {
            val capturedBitmap = try {
                imageConverter.toBitmapWithRotation(imageProxy)
            } catch (_: Throwable) {
                sceneCaptureController.onSceneCaptureError()
                return@launch
            } finally {
                imageProxy.close()
            }

            sceneCaptureController.onSceneBitmapCaptured(capturedBitmap)

            try {
                sceneCaptureController.describeCapturedScene(capturedBitmap)
            } finally {
                sceneCaptureController.finishSceneDescription()
            }
        }
    }

    fun onSceneCaptureError() {
        sceneCaptureController.onSceneCaptureError()
    }

    fun prepareForNextSceneCapture() {
        sceneCaptureController.prepareForNextSceneCapture()
    }

    fun onCurrencyCaptureRequested() {
        currencyRecognitionController.onCaptureRequested()
    }

    fun processCapturedCurrencyImage(imageProxy: ImageProxy) {
        currencyRecognitionController.processCapturedImage(imageProxy, viewModelScope)
    }

    fun onCurrencyCaptureError() {
        currencyRecognitionController.onCaptureError()
    }

    fun prepareForNextCurrencyCapture() {
        currencyRecognitionController.prepareForNextCapture()
    }

    fun onScreenDisposed() {
        if (
            _uiState.value.activeMode == CameraMode.OCR ||
            _uiState.value.activeMode == CameraMode.SCENE_DESCRIPTION ||
            _uiState.value.activeMode == CameraMode.CURRENCY
        ) {
            speechOutput.stop()
        }
        ocrCaptureController.clearLastResult()
        ocrGuidanceController.reset()
        resetCurrencyAnnouncementDebounce()
        currencyRecognitionController.resetBuffer()
        updateUiStateAndRecycleReplacedOcrBitmap { it.copy(ocrCapturedBitmap = null) }
    }

    override fun onCleared() {
        recognizeOcrDocumentUseCase.close()
        ocrGuidanceController.close()
        currencyRecognitionController.close()
        bitmapStore.recycle(_uiState.value.ocrCapturedBitmap)
        bitmapStore.clear()
        super.onCleared()
    }

    private fun refreshLanguageBoundUiText(old: CameraText, new: CameraText) {
        _uiState.update { state ->
            state.copy(
                title = old.translateTitle(state.title, new),
                summary = old.translateSummary(state.summary, new),
                statusMessage = old.translateStatus(state.statusMessage, new),
                ocrGuidanceMessage = old.translateOcrGuidance(state.ocrGuidanceMessage, new),
                lastAnnouncement = old.translateAnnouncement(state.lastAnnouncement, new),
                debugMetrics = old.translateDebug(state.debugMetrics, new)
            )
        }
    }

    private fun updateUiStateAndRecycleReplacedOcrBitmap(
        transform: (CameraUiState) -> CameraUiState
    ) {
        var previousBitmap: Bitmap? = null
        var nextBitmap: Bitmap? = null
        _uiState.update { state ->
            val updatedState = transform(state)
            previousBitmap = state.ocrCapturedBitmap
            nextBitmap = updatedState.ocrCapturedBitmap
            updatedState
        }
        if (previousBitmap !== nextBitmap) bitmapStore.recycle(previousBitmap)
    }
}
