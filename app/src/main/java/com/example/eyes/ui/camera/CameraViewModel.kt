package com.example.eyes.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
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
import com.example.eyes.domain.ocr.OcrPostProcessor
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
import java.util.concurrent.atomic.AtomicInteger
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
    private val lastOcrSwipeAtMs = AtomicReference(0L)
    private val lastOcrGuidanceAtMs = AtomicLong(0L)
    private val lastOcrGuidanceSpeechAtMs = AtomicLong(0L)
    private val lastObjectDetectionAtMs = AtomicLong(0L)
    private val lastCurrencyPreviewAtMs = AtomicLong(0L)
    private val lastObjectAnnouncementAtMs = AtomicLong(0L)
    private val lastCurrencyNoDetectionAtMs = AtomicLong(0L)
    private val lastObjectAnnouncement = AtomicReference("")
    private val lastCurrencyAnnouncement = AtomicReference("")

    private val currentOcrMode = MutableStateFlow(OcrMode.QUICK)

    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val appLanguage = AtomicReference(AppLanguage.VI)
    private val lastRawOcrResult = AtomicReference<OcrResult?>(null)
    private val lastOcrUsedFallback = AtomicBoolean(false)
    private val lastOcrGuidanceBounds = AtomicReference<OcrTextBounds?>(null)
    private val stableOcrGuidanceFrames = AtomicInteger(0)
    private val lastAnnouncedOcrGuidanceStatus = AtomicReference<OcrGuidanceStatus?>(null)
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

        val display = currencyDisplay(label)
        val spoken = currencySpoken(label)
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
                recycleBitmapIfNeeded(bitmap)
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
                replaceLatestFrame(bitmap)
                processObjectDetection(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Object detection frame failed", e)
            } finally {
                recycleBitmapIfNeeded(bitmap)
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
                replaceLatestFrame(bitmap)
                val frame = ocrGuidanceAnalyzer.analyze(bitmap)
                val stableCount = updateOcrGuidanceStability(frame.textBounds)
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
                maybeAnnounceOcrGuidance(evaluation.status, evaluation.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OCR guidance failed", e)
            } finally {
                recycleBitmapIfNeeded(bitmap)
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

    private fun currencyDisplay(label: String): String {
        return when (label) {
            "1000" -> localizedTextProvider.getString(R.string.currency_display_1000, appLanguage.get())
            "2000" -> localizedTextProvider.getString(R.string.currency_display_2000, appLanguage.get())
            "5000" -> localizedTextProvider.getString(R.string.currency_display_5000, appLanguage.get())
            "10000" -> localizedTextProvider.getString(R.string.currency_display_10000, appLanguage.get())
            "20000" -> localizedTextProvider.getString(R.string.currency_display_20000, appLanguage.get())
            "50000" -> localizedTextProvider.getString(R.string.currency_display_50000, appLanguage.get())
            "100000" -> localizedTextProvider.getString(R.string.currency_display_100000, appLanguage.get())
            "200000" -> localizedTextProvider.getString(R.string.currency_display_200000, appLanguage.get())
            "500000" -> localizedTextProvider.getString(R.string.currency_display_500000, appLanguage.get())
            else -> label
        }
    }

    private fun currencySpoken(label: String): String {
        return when (label) {
            "1000" -> localizedTextProvider.getString(R.string.currency_spoken_1000, appLanguage.get())
            "2000" -> localizedTextProvider.getString(R.string.currency_spoken_2000, appLanguage.get())
            "5000" -> localizedTextProvider.getString(R.string.currency_spoken_5000, appLanguage.get())
            "10000" -> localizedTextProvider.getString(R.string.currency_spoken_10000, appLanguage.get())
            "20000" -> localizedTextProvider.getString(R.string.currency_spoken_20000, appLanguage.get())
            "50000" -> localizedTextProvider.getString(R.string.currency_spoken_50000, appLanguage.get())
            "100000" -> localizedTextProvider.getString(R.string.currency_spoken_100000, appLanguage.get())
            "200000" -> localizedTextProvider.getString(R.string.currency_spoken_200000, appLanguage.get())
            "500000" -> localizedTextProvider.getString(R.string.currency_spoken_500000, appLanguage.get())
            else -> label
        }
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
            enterOcrDocumentMode(
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
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasNextOcrSentence) {
            speechOutput.speak(cameraText.endOfText, appLanguage.get().ttsLocale)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex + 1) }
        speakCurrentOcrSentence()
    }

    fun prevOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasPrevOcrSentence) {
            speechOutput.speak(cameraText.startOfText, appLanguage.get().ttsLocale)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex - 1) }
        speakCurrentOcrSentence()
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
        recycleBitmapIfNeeded(_uiState.value.ocrCapturedBitmap)
        super.onCleared()
    }

    private fun updateOcrGuidanceStability(bounds: OcrTextBounds?): Int {
        if (bounds == null) {
            lastOcrGuidanceBounds.set(null)
            stableOcrGuidanceFrames.set(0)
            return 0
        }

        val previous = lastOcrGuidanceBounds.get()
        val stable = previous != null && bounds.isStableComparedTo(previous)
        val nextCount = if (stable) stableOcrGuidanceFrames.incrementAndGet() else 1
        stableOcrGuidanceFrames.set(nextCount)
        lastOcrGuidanceBounds.set(bounds)
        return nextCount
    }

    private fun maybeAnnounceOcrGuidance(status: OcrGuidanceStatus, message: String) {
        if (status != OcrGuidanceStatus.READY) {
            lastAnnouncedOcrGuidanceStatus.set(null)
            return
        }

        val now = System.currentTimeMillis()
        val previousStatus = lastAnnouncedOcrGuidanceStatus.get()
        val elapsed = now - lastOcrGuidanceSpeechAtMs.get()
        if (previousStatus == status && elapsed < OCR_GUIDANCE_SPEECH_INTERVAL_MS) return

        lastAnnouncedOcrGuidanceStatus.set(status)
        lastOcrGuidanceSpeechAtMs.set(now)

        if (previousStatus != OcrGuidanceStatus.READY) {
            hapticService.confirm()
            speechOutput.speak(message, appLanguage.get().ttsLocale)
        }
    }

    private fun resetOcrGuidanceTracking() {
        lastOcrGuidanceBounds.set(null)
        stableOcrGuidanceFrames.set(0)
        lastAnnouncedOcrGuidanceStatus.set(null)
        lastOcrGuidanceSpeechAtMs.set(0L)
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

    private fun OcrTextBounds.isStableComparedTo(other: OcrTextBounds): Boolean {
        val centerDelta = kotlin.math.abs(centerX - other.centerX) + kotlin.math.abs(centerY - other.centerY)
        val areaDelta = kotlin.math.abs(area - other.area)
        return centerDelta < OCR_GUIDANCE_STABLE_CENTER_DELTA && areaDelta < OCR_GUIDANCE_STABLE_AREA_DELTA
    }

    private fun enterOcrDocumentMode(
        result: OcrResult,
        usedFallback: Boolean,
        canTranslateCurrentDocument: Boolean
    ) {
        val sentences = result.sentences.ifEmpty { OcrPostProcessor.splitToSentences(result.fullText) }
        val finalSentences = sentences.ifEmpty { listOf(result.fullText.trim()).filter { it.isNotBlank() } }

        if (finalSentences.isEmpty()) {
            _uiState.update { it.copy(isOcrScanning = false, statusMessage = cameraText.noTextDetectedTryAgain) }
            hapticService.error()
            return
        }

        _uiState.update {
            it.copy(
                isOcrScanning = false,
                ocrSentences = finalSentences,
                ocrCurrentIndex = 0,
                canTranslateCurrentOcrDocument = canTranslateCurrentDocument,
                statusMessage = if (usedFallback) {
                    cameraText.gptFallbackStatus(finalSentences.size)
                } else {
                    cameraText.capturedParagraphs(finalSentences.size)
                },
                lastAnnouncement = finalSentences.first()
            )
        }
        hapticService.confirm()
        speakCurrentOcrSentence()
    }

    private fun speakCurrentOcrSentence() {
        val state = _uiState.value
        if (!state.isOcrDocumentMode) return
        val sentence = state.currentOcrSentence
        val locale = if (looksEnglish(sentence)) Locale.US else VIETNAMESE_LOCALE
        speechOutput.speak(
            cameraText.ocrSentencePosition(state.ocrCurrentIndex + 1, state.ocrSentences.size, sentence),
            locale
        )
    }

    private fun looksEnglish(text: String): Boolean {
        if (text.isBlank()) return false
        if (VI_DIACRITIC_REGEX.containsMatchIn(text)) return false
        return EN_LETTER_REGEX.containsMatchIn(text)
    }

    private fun canHandleOcrSwipe(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastOcrSwipeAtMs.get()
        if (now - last < OCR_SWIPE_DEBOUNCE_MS) return false
        lastOcrSwipeAtMs.set(now)
        return true
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
        if (previousBitmap !== nextBitmap) recycleBitmapIfNeeded(previousBitmap)
    }

    private fun recycleBitmapIfNeeded(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }

    private fun replaceLatestFrame(bitmap: Bitmap) {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val previous = latestFrame.getAndSet(copy)
        recycleBitmapIfNeeded(previous)
    }


    private companion object {
        private const val TAG = "CameraViewModel"
        private const val OCR_SWIPE_DEBOUNCE_MS = 320L
        private const val OCR_GUIDANCE_INTERVAL_MS = 700L
        private const val OCR_GUIDANCE_SPEECH_INTERVAL_MS = 4_000L
        private const val OBJECT_DETECTION_INTERVAL_MS = 1_000L
        private const val CURRENCY_PREVIEW_INTERVAL_MS = 1_500L
        private const val OBJECT_ANNOUNCEMENT_INTERVAL_MS = 3_000L
        private const val OBJECT_ANNOUNCEMENT_REPEAT_MS = 6_000L
        private const val CURRENCY_NO_DETECTION_REPEAT_MS = 10_000L
        private const val OCR_GUIDANCE_STABLE_CENTER_DELTA = 0.12f
        private const val OCR_GUIDANCE_STABLE_AREA_DELTA = 0.15f
        private val VIETNAMESE_LOCALE: Locale = Locale.Builder().setLanguage("vi").setRegion("VN").build()
        private val VI_DIACRITIC_REGEX = Regex(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            RegexOption.IGNORE_CASE
        )
        private val EN_LETTER_REGEX = Regex("[A-Za-z]")
    }
}
