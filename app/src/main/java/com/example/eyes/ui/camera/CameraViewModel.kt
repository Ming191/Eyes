package com.example.eyes.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.application.camera.ObserveCameraPreferencesUseCase
import com.example.eyes.application.navigation.SetCameraOcrModeUseCase
import com.example.eyes.application.objectdetection.DetectObjectsUseCase
import com.example.eyes.application.objectdetection.WarmUpObjectDetectionUseCase
import com.example.eyes.application.ocr.RecognizeOcrDocumentInput
import com.example.eyes.application.ocr.RecognizeOcrDocumentUseCase
import com.example.eyes.application.ports.CurrencyRecognizerFactory
import com.example.eyes.application.ports.CurrencyRecognizerPort
import com.example.eyes.application.scene.DescribeSceneUseCase
import com.example.eyes.infrastructure.camera.toBitmapWithRotation
import com.example.eyes.infrastructure.camera.toImageFrame
import com.example.eyes.domain.audio.AudioRouteProvider
import com.example.eyes.domain.haptics.HapticFeedback
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionError
import com.example.eyes.domain.voice.VoiceCommandRepository
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.infrastructure.ocr.MlKitOcrGuidanceAnalyzer
import com.example.eyes.domain.ocr.OcrGuidanceEvaluator
import com.example.eyes.domain.ocr.OcrGuidanceStatus
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.ocr.OcrResult
import com.example.eyes.domain.ocr.OcrTextBounds
import com.example.eyes.domain.speech.SpeechOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
    private val ocrGuidanceAnalyzer: MlKitOcrGuidanceAnalyzer,
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
    private val localizedTextProvider: LocalizedTextProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraText.from(localizedTextProvider, AppLanguage.VI).initialUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingOcrGuidance = AtomicBoolean(false)
    private val isProcessingObjectDetection = AtomicBoolean(false)
    private val isProcessingCurrencyPreview = AtomicBoolean(false)
    private val lastOcrGuidanceAtMs = AtomicLong(0L)
    private val lastObjectDetectionAtMs = AtomicLong(0L)
    private val lastCurrencyPreviewAtMs = AtomicLong(0L)
    private val lastObjectAnnouncementAtMs = AtomicLong(0L)
    private val lastCurrencyNoDetectionAtMs = AtomicLong(0L)
    private val lastObjectAnnouncement = AtomicReference("")
    private val lastCurrencyAnnouncement = AtomicReference("")

    private val currentOcrMode = MutableStateFlow(OcrMode.QUICK)

    private val bitmapStore = CameraBitmapStore()
    private val appLanguage = AtomicReference(AppLanguage.VI)
    private val lastRawOcrResult = AtomicReference<OcrResult?>(null)
    private val lastOcrUsedFallback = AtomicBoolean(false)
    private val ocrGuidanceTracker = OcrGuidanceTracker()
    private val currencyTextMapper = CurrencyTextMapper(localizedTextProvider) { appLanguage.get() }
    private val ocrDocumentController = OcrDocumentController(
        uiState = _uiState,
        speechOutput = speechOutput,
        hapticService = hapticService,
        cameraText = { cameraText },
        appLanguage = { appLanguage.get() }
    )
    private var currencyAnalyzer: CurrencyRecognizerPort? = null
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
        warmUpObjectDetectionModel()
    }

    fun setAppLanguage(language: AppLanguage) {
        val previousLanguage = appLanguage.get()
        if (previousLanguage == language) return

        val previousText = cameraText
        appLanguage.set(language)
        refreshLanguageBoundUiText(previousText, cameraText)
    }

    private fun warmUpObjectDetectionModel() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val outputs = warmUpObjectDetectionUseCase()
                if (_uiState.value.activeMode == CameraMode.OBJECT_DETECTION) {
                    val message = cameraText.objectDetectionWarmupDone
                    Log.i(TAG, "Object detection warmup done: $outputs")
                    _uiState.update {
                        it.copy(
                            statusMessage = message,
                            debugMetrics = outputs.joinToString(prefix = "YOLO: ") { output ->
                                "#${output.index} ${output.shape} ${output.dtype} n=${output.elementCount}"
                            }
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_uiState.value.activeMode == CameraMode.OBJECT_DETECTION) {
                    val message = cameraText.objectDetectionWarmupFailed
                    Log.e(TAG, "Object detection warmup failed", error)
                    _uiState.update {
                        it.copy(
                            statusMessage = message,
                            debugMetrics = "YOLO: ${error.message ?: error::class.java.simpleName}"
                        )
                    }
                }
            }
        }
    }

    private fun getCurrencyAnalyzer(): CurrencyRecognizerPort? {
        currencyAnalyzer?.let { return it }
        return try {
            currencyRecognizerFactory.create(::onCurrencyResult).also { analyzer ->
                currencyAnalyzer = analyzer
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Currency model load failed", error)
            val message = cameraText.currencyModelLoadError
            _uiState.update {
                it.copy(
                    statusMessage = message,
                    lastAnnouncement = message,
                    currencyDisplay = "",
                    currencyConfidence = 0f
                )
            }
            null
        }
    }

    private fun onCurrencyResult(label: String, confidence: Float) {
        if (_uiState.value.activeMode != CameraMode.CURRENCY) return
        val safeConfidence = confidence.coerceIn(0f, 1f)

        if (label == CurrencyRecognizerPort.EMPTY_LABEL) {
            val hadResult = _uiState.value.currencyDisplay.isNotEmpty()
            if (hadResult) {
                resetCurrencyAnnouncementDebounce()
                currencyAnalyzer?.resetBuffer()
            }
            maybeSpeakNoCurrencyDetected()
            _uiState.update { state ->
                if (state.activeMode != CameraMode.CURRENCY) {
                    state
                } else {
                    state.copy(
                        currencyDisplay = "",
                        currencyConfidence = 0f,
                        isCurrencyScanning = false,
                        statusMessage = cameraText.noCurrencyDetected,
                        lastAnnouncement = cameraText.noCurrencyDetected
                    )
                }
            }
            return
        }

        val display = currencyTextMapper.display(label)
        val spoken = currencyTextMapper.spoken(label)
        val confidencePercent = String.format(Locale.getDefault(), "%.0f%%", safeConfidence * 100f)

        if (lastCurrencyAnnouncement.get() != label) {
            lastCurrencyAnnouncement.set(label)
            hapticService.confirm()
            speechOutput.speak(spoken, appLanguage.get().ttsLocale)
        }

        _uiState.update { state ->
            if (state.activeMode != CameraMode.CURRENCY) {
                state
            } else {
                state.copy(
                    currencyDisplay = display,
                    currencyConfidence = safeConfidence,
                    isCurrencyScanning = false,
                    statusMessage = cameraText.currencyDetectedStatus(display, confidencePercent),
                    lastAnnouncement = spoken,
                    debugMetrics = cameraText.currencyDebug(confidencePercent)
                )
            }
        }
    }

    private fun applyVoiceCameraCommand(
        mode: CameraMode,
        title: String = cameraText.ocrTitle,
        summary: String = cameraText.ocrSummary,
        statusMessage: String
    ) {
        resetObjectAnnouncementDebounce()
        resetCurrencyAnnouncementDebounce()
        _uiState.update {
            it
                .resetOcrRuntime()
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

    private fun CameraUiState.resetOcrRuntime(
        guidanceMessage: String = cameraText.ocrGuidanceSearch
    ): CameraUiState = copy(
        isOcrScanning = false,
        ocrSentences = emptyList(),
        ocrCurrentIndex = 0,
        canTranslateCurrentOcrDocument = false,
        ocrCapturedBitmap = null,
        ocrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
        ocrGuidanceMessage = guidanceMessage,
        isOcrReadyToCapture = false,
        ocrGuidanceBounds = null
    )

    private fun CameraUiState.resetDetectionAndCurrency(): CameraUiState = copy(
        objectDetections = emptyList(),
        currencyDisplay = "",
        currencyConfidence = 0f,
        isCurrencyScanning = false
    )

    fun processFrame(imageProxy: ImageProxy) {
        when (_uiState.value.activeMode) {
            CameraMode.OCR -> processOcrGuidanceImageProxy(imageProxy)
            CameraMode.SCENE_DESCRIPTION -> imageProxy.close()
            CameraMode.OBJECT_DETECTION -> processObjectDetectionImageProxy(imageProxy)
            CameraMode.CURRENCY -> processCurrencyPreviewImageProxy(imageProxy)
        }
    }

    private fun processCurrencyPreviewImageProxy(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastCurrencyPreviewAtMs.get() < CURRENCY_PREVIEW_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        if (!isProcessingCurrencyPreview.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastCurrencyPreviewAtMs.set(now)

        viewModelScope.launch(Dispatchers.Default) {
            var bitmap: Bitmap? = null
            try {
	            val analyzer = getCurrencyAnalyzer() ?: return@launch
	            bitmap = imageProxy.toBitmapWithRotation()
                analyzer.analyze(bitmap.toImageFrame())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Currency preview frame failed", error)
            } finally {
                bitmapStore.recycle(bitmap)
                imageProxy.close()
                isProcessingCurrencyPreview.set(false)
            }
        }
    }

    private fun processObjectDetectionImageProxy(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastObjectDetectionAtMs.get() < OBJECT_DETECTION_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        if (!isProcessingObjectDetection.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastObjectDetectionAtMs.set(now)

        viewModelScope.launch(Dispatchers.Default) {
            var bitmap: Bitmap? = null
            try {
                bitmap = imageProxy.toBitmapWithRotation()
                bitmapStore.replaceLatestFrame(bitmap)
                processObjectDetection(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Object detection frame failed", e)
            } finally {
                bitmapStore.recycle(bitmap)
                imageProxy.close()
                isProcessingObjectDetection.set(false)
            }
        }
    }

    private fun processOcrGuidanceImageProxy(imageProxy: ImageProxy) {
        val state = _uiState.value
        if (state.isOcrScanning || state.isOcrDocumentMode || state.ocrCapturedBitmap != null) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastOcrGuidanceAtMs.get() < OCR_GUIDANCE_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        if (!isProcessingOcrGuidance.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastOcrGuidanceAtMs.set(now)

        viewModelScope.launch(Dispatchers.Default) {
            var bitmap: Bitmap? = null
            try {
                bitmap = imageProxy.toBitmapWithRotation()
                bitmapStore.replaceLatestFrame(bitmap)
                val frame = ocrGuidanceAnalyzer.analyze(bitmap)
                val stableCount = ocrGuidanceTracker.updateStability(frame.textBounds)
                val evaluation = OcrGuidanceEvaluator.evaluate(
                    frame = frame,
                    stableFrameCount = stableCount,
                    text = cameraText.ocrGuidanceText()
                )
                val currentState = _uiState.value
                if (
                    currentState.activeMode != CameraMode.OCR ||
                    currentState.isOcrScanning ||
                    currentState.isOcrDocumentMode ||
                    currentState.ocrCapturedBitmap != null
                ) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        ocrGuidanceStatus = evaluation.status,
                        ocrGuidanceMessage = evaluation.message,
                        isOcrReadyToCapture = evaluation.isReadyToCapture,
                        ocrGuidanceBounds = evaluation.textBounds,
                        statusMessage = evaluation.message,
                        lastAnnouncement = evaluation.message
                    )
                }
                announceOcrGuidanceIfNeeded(evaluation.status, evaluation.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OCR guidance failed", e)
            } finally {
                bitmapStore.recycle(bitmap)
                imageProxy.close()
                isProcessingOcrGuidance.set(false)
            }
        }
    }

    private suspend fun processObjectDetection(bitmap: Bitmap) {
        try {
            val detections = detectObjectsUseCase(bitmap.toImageFrame())
            if (_uiState.value.activeMode != CameraMode.OBJECT_DETECTION) return

            val language = appLanguage.get()
            val overlayItems = detections.map { detection ->
                detection.toOverlayItem(bitmap.width, bitmap.height, localizedTextProvider, language)
            }
            Log.i(TAG, "Object detection frame: ${overlayItems.size} detections")
            val announcement = if (overlayItems.isNotEmpty()) {
                overlayItems.joinToString(separator = ". ") { item ->
                    cameraText.objectDetectionAnnouncement(item.label, item.positionText)
                }
            } else {
                cameraText.objectDetectionNoObjects
            }
            if (_uiState.value.activeMode != CameraMode.OBJECT_DETECTION) return
            maybeSpeakObjectDetection(announcement, overlayItems.isNotEmpty())
            _uiState.update { state ->
                if (state.activeMode != CameraMode.OBJECT_DETECTION) {
                    state
                } else {
                    state.copy(
                        objectDetections = overlayItems,
                        lastAnnouncement = announcement,
                        debugMetrics = cameraText.objectDetectionDebug(overlayItems.size)
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Object detection failed", error)
            _uiState.update { state ->
                if (state.activeMode != CameraMode.OBJECT_DETECTION) {
                    state
                } else {
                    state.copy(
                        objectDetections = emptyList(),
                        debugMetrics = "YOLO detect: ${error.message ?: error::class.java.simpleName}"
                    )
                }
            }
        }
    }

    private fun resetObjectAnnouncementDebounce() {
        lastObjectAnnouncement.set("")
        lastObjectAnnouncementAtMs.set(0L)
    }

    private fun resetCurrencyAnnouncementDebounce() {
        lastCurrencyAnnouncement.set("")
        lastCurrencyNoDetectionAtMs.set(0L)
    }

    private fun maybeSpeakNoCurrencyDetected() {
        val now = System.currentTimeMillis()
        if (now - lastCurrencyNoDetectionAtMs.get() < CURRENCY_NO_DETECTION_REPEAT_MS) return

        lastCurrencyNoDetectionAtMs.set(now)
        speechOutput.speak(
            cameraText.noCurrencyDetected,
            appLanguage.get().ttsLocale
        )
    }

    private fun maybeSpeakObjectDetection(
        announcement: String,
        hasObjects: Boolean
    ) {
        if (!hasObjects) return
        val now = System.currentTimeMillis()
        val previous = lastObjectAnnouncement.get()
        if (announcement == previous && now - lastObjectAnnouncementAtMs.get() < OBJECT_ANNOUNCEMENT_REPEAT_MS) return
        if (announcement != previous && now - lastObjectAnnouncementAtMs.get() < OBJECT_ANNOUNCEMENT_INTERVAL_MS) return

        lastObjectAnnouncement.set(announcement)
        lastObjectAnnouncementAtMs.set(now)
        Log.i(TAG, "Object detection TTS: $announcement")
        speechOutput.speak(
            text = announcement,
            locale = appLanguage.get().ttsLocale
        )
    }

    fun processCapturedOcrImage(imageProxy: ImageProxy) {
        viewModelScope.launch(Dispatchers.Default) {
            resetOcrGuidanceTracking()
            val bitmap = try {
                imageProxy.toBitmapWithRotation()
            } catch (_: Throwable) {
                _uiState.update { it.copy(isOcrScanning = false, statusMessage = cameraText.cannotProcessCapturedImage) }
                hapticService.error()
                return@launch
            } finally {
                imageProxy.close()
            }

            updateUiStateAndRecycleReplacedOcrBitmap {
                it.resetOcrRuntime(guidanceMessage = cameraText.processingOcrImage).copy(
                    isOcrScanning = true,
                    ocrCapturedBitmap = bitmap,
                    statusMessage = cameraText.processingOcrImage
                )
            }

            val recognizedDocument = runCatching {
                recognizeOcrDocumentUseCase(
                    RecognizeOcrDocumentInput(
                        imageFrame = bitmap.toImageFrame(),
                        mode = currentOcrMode.value,
                        translateToVietnamese = _uiState.value.ocrTranslateToVietnamese,
                        strings = cameraText.ocrDocumentStrings(),
                        onTranslateFailure = {
                            speechOutput.speak(
                                cameraText.cannotTranslateReadingOriginal,
                                appLanguage.get().ttsLocale
                            )
                        }
                    )
                )
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(isOcrScanning = false, statusMessage = cameraText.ocrFailed(error.message ?: cameraText.unknownReason))
                }
                hapticService.error()
                return@launch
            }
            lastRawOcrResult.set(recognizedDocument.rawResult)
            lastOcrUsedFallback.set(recognizedDocument.usedFallbackFromAccuracy)
            if (recognizedDocument.usedFallbackFromAccuracy) {
                setCameraOcrModeUseCase(OcrMode.QUICK)
                val reason = recognizedDocument.fallbackReason ?: cameraText.unknownError
                speechOutput.speak(
                    cameraText.accuracyOcrFallback(reason),
                    appLanguage.get().ttsLocale
                )
            }
            ocrDocumentController.enterOcrDocumentMode(
                result = recognizedDocument.resultForSpeech,
                usedFallback = recognizedDocument.usedFallbackFromAccuracy,
                canTranslateCurrentDocument = recognizedDocument.canTranslateDocument
            )
        }
    }

    fun onOcrCaptureError() {
        _uiState.update { it.copy(isOcrScanning = false, statusMessage = cameraText.cannotCaptureOcrTryAgain) }
        hapticService.error()
    }

    fun onOcrCaptureRequested() {
        if (!_uiState.value.isOcrReadyToCapture) {
            speechOutput.speak(cameraText.imageMayBeUnclearCapturing, appLanguage.get().ttsLocale)
        }
    }

    fun prepareForNextOcrCapture() {
        lastRawOcrResult.set(null)
        lastOcrUsedFallback.set(false)
        resetOcrGuidanceTracking()
        _uiState.update {
            it.resetOcrRuntime().copy(
                statusMessage = cameraText.readyToCaptureOcr,
                lastAnnouncement = cameraText.readyForNewCapture
            )
        }
        speechOutput.stop()
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
            currencyAnalyzer?.resetBuffer()
        }
        resetOcrGuidanceTracking()
        resetObjectAnnouncementDebounce()
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
                    it.resetOcrRuntime()
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
                    it.resetOcrRuntime()
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
                    it.resetOcrRuntime()
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
                currencyAnalyzer?.resetBuffer()
                hapticService.confirm()
                if (!audioRouteProvider.isHeadsetConnected()) {
                    speechOutput.speak(cameraText.switchedToCurrencyMode, appLanguage.get().ttsLocale)
                }
                _uiState.update {
                    it.resetOcrRuntime()
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
        if (_uiState.value.isDescribingScene || _uiState.value.ocrCapturedBitmap != null) return
        _uiState.update {
            it.copy(
                isDescribingScene = true,
                statusMessage = cameraText.describingScenePleaseWait,
                lastAnnouncement = cameraText.describingScenePleaseWait
            )
        }
        hapticService.loading()
    }

    fun processCapturedSceneImage(imageProxy: ImageProxy) {
        viewModelScope.launch(Dispatchers.Default) {
            val capturedBitmap = try {
                imageProxy.toBitmapWithRotation()
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isDescribingScene = false,
                        statusMessage = cameraText.cannotCaptureSceneTryAgain,
                        lastAnnouncement = cameraText.cannotCaptureSceneTryAgain
                    )
                }
                hapticService.error()
                return@launch
            } finally {
                imageProxy.close()
            }

            updateUiStateAndRecycleReplacedOcrBitmap {
                it.copy(
                    ocrCapturedBitmap = capturedBitmap,
                    isDescribingScene = true,
                    statusMessage = cameraText.describingScenePleaseWait,
                    lastAnnouncement = cameraText.describingScenePleaseWait
                )
            }

            try {
                describeCapturedScene(capturedBitmap)
            } finally {
                _uiState.update { it.copy(isDescribingScene = false) }
            }
        }
    }

    fun onSceneCaptureError() {
        _uiState.update {
            it.copy(
                isDescribingScene = false,
                statusMessage = cameraText.cannotCaptureSceneTryAgain,
                lastAnnouncement = cameraText.cannotCaptureSceneTryAgain
            )
        }
        hapticService.error()
    }

    fun prepareForNextSceneCapture() {
        updateUiStateAndRecycleReplacedOcrBitmap {
            it.copy(
                ocrCapturedBitmap = null,
                isDescribingScene = false,
                statusMessage = cameraText.waitingForSceneFrame,
                lastAnnouncement = cameraText.waitingForSceneFrame
            )
        }
        speechOutput.stop()
    }

    fun onCurrencyCaptureRequested() {
        val state = _uiState.value
        if (state.isCurrencyScanning || state.ocrCapturedBitmap != null) return
        _uiState.update {
            it.copy(
                isCurrencyScanning = true,
                statusMessage = cameraText.processingCurrencyImage,
                lastAnnouncement = cameraText.processingCurrencyImage
            )
        }
        hapticService.loading()
        speechOutput.speak(
            cameraText.processingCurrencyImage,
            appLanguage.get().ttsLocale
        )
    }

    fun processCapturedCurrencyImage(imageProxy: ImageProxy) {
        viewModelScope.launch(Dispatchers.Default) {
            val capturedBitmap = try {
                imageProxy.toBitmapWithRotation()
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isCurrencyScanning = false,
                        statusMessage = cameraText.cannotCaptureCurrencyTryAgain,
                        lastAnnouncement = cameraText.cannotCaptureCurrencyTryAgain
                    )
                }
                hapticService.error()
                return@launch
            } finally {
                imageProxy.close()
            }

            updateUiStateAndRecycleReplacedOcrBitmap {
                it.copy(
                    ocrCapturedBitmap = capturedBitmap,
                    isCurrencyScanning = true,
                    currencyDisplay = "",
                    currencyConfidence = 0f,
                    statusMessage = cameraText.processingCurrencyImage,
                    lastAnnouncement = cameraText.processingCurrencyImage
                )
            }

            val analyzer = getCurrencyAnalyzer()
            if (analyzer == null) {
                _uiState.update {
                    it.copy(
                        isCurrencyScanning = false,
                        statusMessage = cameraText.currencyModelInitError,
                        lastAnnouncement = cameraText.currencyModelInitError
                    )
                }
                hapticService.error()
                return@launch
            }

            runCatching {
                analyzer.resetBuffer()
                analyzer.analyze(capturedBitmap.toImageFrame())
            }.onFailure { error ->
                Log.e(TAG, "Currency capture failed", error)
                _uiState.update {
                    it.copy(
                        isCurrencyScanning = false,
                        statusMessage = cameraText.cannotCaptureCurrencyTryAgain,
                        lastAnnouncement = cameraText.cannotCaptureCurrencyTryAgain
                    )
                }
                hapticService.error()
            }
        }
    }

    fun onCurrencyCaptureError() {
        _uiState.update {
            it.copy(
                isCurrencyScanning = false,
                statusMessage = cameraText.cannotCaptureCurrencyTryAgain,
                lastAnnouncement = cameraText.cannotCaptureCurrencyTryAgain
            )
        }
        hapticService.error()
    }

    fun prepareForNextCurrencyCapture() {
        resetCurrencyAnnouncementDebounce()
        currencyAnalyzer?.resetBuffer()
        updateUiStateAndRecycleReplacedOcrBitmap {
            it.copy(
                ocrCapturedBitmap = null,
                isCurrencyScanning = false,
                currencyDisplay = "",
                currencyConfidence = 0f,
                statusMessage = cameraText.waitingForClearMoneyImage,
                lastAnnouncement = cameraText.waitingForClearMoneyImage
            )
        }
        speechOutput.stop()
    }

    private suspend fun describeCapturedScene(capturedBitmap: Bitmap) {
        try {
            val language = appLanguage.get()
            when (val result = describeSceneUseCase(imageFrame = capturedBitmap.toImageFrame(), language = language)) {
                is SceneDescription.Success -> {
                    if (!audioRouteProvider.isHeadsetConnected()) {
                        speechOutput.speak(result.text, language.ttsLocale)
                    }
                    hapticService.confirm()
                    _uiState.update {
                        it.copy(
                            statusMessage = cameraText.sceneDescriptionDone,
                            lastAnnouncement = result.text
                        )
                    }
                }

                is SceneDescription.Failure -> {
                    if (result.error == SceneDescriptionError.OFFLINE) {
                        val fallback = cameraText.sceneDescriptionOfflineFallback
                        if (!audioRouteProvider.isHeadsetConnected()) {
                            speechOutput.speak(fallback, language.ttsLocale)
                        }
                        hapticService.confirm()
                        _uiState.update {
                            it.copy(
                                statusMessage = cameraText.sceneDescriptionDone,
                                lastAnnouncement = fallback
                            )
                        }
                        return
                    }
                    val userMessage = cameraText.sceneDescriptionError(result.error)
                    if (!audioRouteProvider.isHeadsetConnected()) {
                        speechOutput.speak(userMessage, language.ttsLocale)
                    }
                    hapticService.error()
                    _uiState.update {
                        it.copy(
                            statusMessage = cameraText.sceneDescriptionFailed,
                            lastAnnouncement = userMessage
                        )
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Describe captured scene failed", error)
            _uiState.update {
                it.copy(
                    statusMessage = cameraText.sceneDescriptionFailed,
                    lastAnnouncement = cameraText.sceneDescriptionFailed
                )
            }
            hapticService.error()
        }
    }

    fun onScreenDisposed() {
        if (
            _uiState.value.activeMode == CameraMode.OCR ||
            _uiState.value.activeMode == CameraMode.SCENE_DESCRIPTION ||
            _uiState.value.activeMode == CameraMode.CURRENCY
        ) {
            speechOutput.stop()
        }
        lastRawOcrResult.set(null)
        lastOcrUsedFallback.set(false)
        resetOcrGuidanceTracking()
        resetCurrencyAnnouncementDebounce()
        currencyAnalyzer?.resetBuffer()
        updateUiStateAndRecycleReplacedOcrBitmap { it.copy(ocrCapturedBitmap = null) }
    }

    override fun onCleared() {
        recognizeOcrDocumentUseCase.close()
        ocrGuidanceAnalyzer.close()
        currencyAnalyzer?.close()
        bitmapStore.recycle(_uiState.value.ocrCapturedBitmap)
        bitmapStore.clear()
        super.onCleared()
    }

    private fun announceOcrGuidanceIfNeeded(status: OcrGuidanceStatus, message: String) {
        if (!ocrGuidanceTracker.shouldAnnounce(status)) return
        hapticService.confirm()
        speechOutput.speak(message, appLanguage.get().ttsLocale)
    }

    private fun resetOcrGuidanceTracking() {
        ocrGuidanceTracker.reset()
        lastOcrGuidanceAtMs.set(0L)
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


    private companion object {
        private const val TAG = "CameraViewModel"
        private const val OCR_GUIDANCE_INTERVAL_MS = 700L
        private const val OBJECT_DETECTION_INTERVAL_MS = 1_000L
        private const val CURRENCY_PREVIEW_INTERVAL_MS = 1_500L
        private const val OBJECT_ANNOUNCEMENT_INTERVAL_MS = 3_000L
        private const val OBJECT_ANNOUNCEMENT_REPEAT_MS = 6_000L
        private const val CURRENCY_NO_DETECTION_REPEAT_MS = 10_000L
    }
}
