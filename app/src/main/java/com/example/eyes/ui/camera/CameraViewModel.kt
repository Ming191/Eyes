package com.example.eyes.ui.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.application.objectdetection.DetectObjectsUseCase
import com.example.eyes.application.objectdetection.WarmUpObjectDetectionUseCase
import com.example.eyes.application.ocr.RecognizeOcrDocumentInput
import com.example.eyes.application.ocr.RecognizeOcrDocumentStrings
import com.example.eyes.application.ocr.RecognizeOcrDocumentUseCase
import com.example.eyes.application.scene.DescribeSceneUseCase
import com.example.eyes.camera.CurrencyAnalyzer
import com.example.eyes.camera.toBitmapWithRotation
import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionError
import com.example.eyes.domain.voice.VoiceCommandRepository
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.ocr.MlKitOcrGuidanceAnalyzer
import com.example.eyes.ocr.OcrGuidanceEvaluator
import com.example.eyes.ocr.OcrGuidanceEvaluator.OcrGuidanceText
import com.example.eyes.ocr.OcrGuidanceStatus
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult
import com.example.eyes.ocr.OcrTextBounds
import com.example.eyes.objectdetection.localizedText
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
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
enum class CameraMode(
) {
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
    private val ttsService: TtsService,
    private val hapticService: HapticService,
    private val dataStoreManager: DataStoreManager,
    private val voiceCommandRepository: VoiceCommandRepository,
    private val describeSceneUseCase: DescribeSceneUseCase,
    private val detectObjectsUseCase: DetectObjectsUseCase,
    private val warmUpObjectDetectionUseCase: WarmUpObjectDetectionUseCase,
    private val audioManager: AudioManager,
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
    private var currencyAnalyzer: CurrencyAnalyzer? = null
    private val cameraText: CameraText get() = CameraText.from(localizedTextProvider, appLanguage.get())

    init {
        viewModelScope.launch {
            dataStoreManager.appLanguageFlow.collect { language ->
                setAppLanguage(language)
            }
        }
        viewModelScope.launch {
            dataStoreManager.ocrModeFlow.collect { mode ->
                currentOcrMode.value = mode
                _uiState.update { it.copy(ocrMode = mode) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.ocrTranslateToVietnameseFlow.collect { enabled ->
                _uiState.update { it.copy(ocrTranslateToVietnamese = enabled) }
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

    private fun getCurrencyAnalyzer(): CurrencyAnalyzer? {
        currencyAnalyzer?.let { return it }
        return try {
            CurrencyAnalyzer(localizedTextProvider.applicationContext, ::onCurrencyResult).also { analyzer ->
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

        if (label == CurrencyAnalyzer.EMPTY_LABEL) {
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
            ttsService.speak(spoken, appLanguage.get().ttsLocale)
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
            try {
                val analyzer = getCurrencyAnalyzer()
                if (analyzer == null) {
                    imageProxy.close()
                    return@launch
                }
                analyzer.analyze(imageProxy)
            } catch (error: CancellationException) {
                imageProxy.close()
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Currency preview frame failed", error)
                imageProxy.close()
            } finally {
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
            val detections = detectObjectsUseCase(bitmap)
            if (_uiState.value.activeMode != CameraMode.OBJECT_DETECTION) return

            val overlayItems = detections.map { detection ->
                detection.toOverlayItem(bitmap.width, bitmap.height)
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
        ttsService.speak(
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
        ttsService.speak(
            text = announcement,
            locale = appLanguage.get().ttsLocale
        )
    }

    private fun com.example.eyes.objectdetection.Detection.toOverlayItem(
        frameWidth: Int,
        frameHeight: Int
    ): DetectionOverlayItem {
        val box: RectF = boundingBox
        return DetectionOverlayItem(
            label = localizedObjectDetectionLabel(classId, label),
            confidence = confidence,
            positionText = position.localizedText(localizedTextProvider, appLanguage.get()),
            left = box.left / frameWidth,
            top = box.top / frameHeight,
            right = box.right / frameWidth,
            bottom = box.bottom / frameHeight,
            sourceAspectRatio = frameWidth.toFloat() / frameHeight.toFloat()
        )
    }

    private fun localizedObjectDetectionLabel(classId: Int, fallback: String): String {
        val labels = localizedTextProvider.getStringArray(R.array.object_detection_coco_labels, appLanguage.get())
        return labels.getOrNull(classId) ?: fallback
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
                        bitmap = bitmap,
                        mode = currentOcrMode.value,
                        translateToVietnamese = _uiState.value.ocrTranslateToVietnamese,
                        strings = cameraText.ocrDocumentStrings(),
                        onTranslateFailure = {
                            ttsService.speak(
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
                dataStoreManager.setOcrMode(OcrMode.QUICK)
                val reason = recognizedDocument.fallbackReason ?: cameraText.unknownError
                ttsService.speak(
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
            ttsService.speak(cameraText.imageMayBeUnclearCapturing, appLanguage.get().ttsLocale)
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
        ttsService.stop()
    }

    fun nextOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasNextOcrSentence) {
            ttsService.speak(cameraText.endOfText, appLanguage.get().ttsLocale)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex + 1) }
        speakCurrentOcrSentence()
    }

    fun prevOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasPrevOcrSentence) {
            ttsService.speak(cameraText.startOfText, appLanguage.get().ttsLocale)
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
                if (!isHeadsetConnected()) {
                    val modeLabel = cameraText.ocrModeLabel(currentOcrMode.value)
                    ttsService.speak(cameraText.switchedToOcrMode(modeLabel), appLanguage.get().ttsLocale)
                }
                ttsService.warmupLocale(Locale.US)
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
                if (!isHeadsetConnected()) {
                    ttsService.speak(
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
                if (!isHeadsetConnected()) {
                    ttsService.speak(cameraText.switchedToObjectDetectionMode, appLanguage.get().ttsLocale)
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
                if (!isHeadsetConnected()) {
                    ttsService.speak(cameraText.switchedToCurrencyMode, appLanguage.get().ttsLocale)
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
            dataStoreManager.setOcrMode(mode)
            hapticService.confirm()
            ttsService.speak(
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
        ttsService.stop()
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
        ttsService.speak(
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
                analyzer.analyze(capturedBitmap)
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
        ttsService.stop()
    }

    private suspend fun describeCapturedScene(capturedBitmap: Bitmap) {
        try {
            val language = appLanguage.get()
            when (val result = describeSceneUseCase(bitmap = capturedBitmap, language = language)) {
                is SceneDescription.Success -> {
                    if (!isHeadsetConnected()) {
                        ttsService.speak(result.text, language.ttsLocale)
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
                        if (!isHeadsetConnected()) {
                            ttsService.speak(fallback, language.ttsLocale)
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
                    if (!isHeadsetConnected()) {
                        ttsService.speak(userMessage, language.ttsLocale)
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
            ttsService.stop()
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
            ttsService.speak(message, appLanguage.get().ttsLocale)
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
        val sentences = if (result.sentences.isNotEmpty()) result.sentences else OcrPostProcessor.splitToSentences(result.fullText)
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
        ttsService.speak(
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

    @SuppressLint("ObsoleteSdkInt")
    private fun isHeadsetConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
        }
    }

    private data class CameraText(
        val ocrGuidanceSearch: String,
        val ocrGuidanceTooDark: String,
        val ocrGuidanceTooBright: String,
        val ocrGuidanceMoveCloser: String,
        val ocrGuidanceMoveBack: String,
        val ocrGuidanceTextClipped: String,
        val ocrGuidanceMoveLeft: String,
        val ocrGuidanceMoveRight: String,
        val ocrGuidanceMoveUp: String,
        val ocrGuidanceMoveDown: String,
        val ocrGuidanceReady: String,
        val ocrGuidanceHoldSteady: String,
        val initialStatus: String,
        val initialAnnouncement: String,
        val initialDebug: String,
        val ocrTitle: String,
        val ocrSummary: String,
        val sceneDescriptionTitle: String,
        val sceneDescriptionSummary: String,
        val currencyTitle: String,
        val currencySummary: String,
        val currencyInstruction: String,
        val currencyModelLoadError: String,
        val currencyModelInitError: String,
        val currencyDetectedStatusTemplate: String,
        val currencyDebugTemplate: String,
        val currencyDebugWaiting: String,
        val noCurrencyDetected: String,
        val waitingForClearTextFrame: String,
        val waitingForSceneFrame: String,
        val waitingForClearMoneyImage: String,
        val frameUnclearRetrying: String,
        val cannotProcessCapturedImage: String,
        val processingOcrImage: String,
        val processingCurrencyImage: String,
        val unknownReason: String,
        val unknownError: String,
        val cannotCaptureOcrTryAgain: String,
        val cannotCaptureCurrencyTryAgain: String,
        val imageMayBeUnclearCapturing: String,
        val readyToCaptureOcr: String,
        val readyForNewCapture: String,
        val endOfText: String,
        val startOfText: String,
        val switchedToReadTextMode: String,
        val ocrDebugWaiting: String,
        val enabledEnglishToVietnameseTranslation: String,
        val disabledEnglishToVietnameseTranslation: String,
        val cannotCaptureSceneTryAgain: String,
        val describingScenePleaseWait: String,
        val sceneDescriptionDone: String,
        val sceneDescriptionFailed: String,
        val sceneDescriptionErrorApiKeyMissing: String,
        val sceneDescriptionErrorUnauthorized: String,
        val sceneDescriptionErrorQuota: String,
        val sceneDescriptionErrorTimeout: String,
        val sceneDescriptionErrorGeneric: String,
        val sceneDescriptionOfflineFallback: String,
        val gptRefusedReason: String,
        val noTextDetectedTryAgain: String,
        val updatingOcrReading: String,
        val apiKeyReason: String,
        val modelPermissionReason: String,
        val quotaReason: String,
        val timeoutReason: String,
        val translationUnchangedReason: String,
        val cannotTranslateReadingOriginal: String,
        val ocrFailedTemplate: String,
        val accuracyOcrFallbackTemplate: String,
        val quickModeLabel: String,
        val accurateModeLabel: String,
        val switchedToOcrModeTemplate: String,
        val switchedToQuickMode: String,
        val switchedToAccurateMode: String,
        val gptFallbackStatusTemplate: String,
        val capturedParagraphsTemplate: String,
        val ocrSentencePositionTemplate: String,
        val objectDetectionWarmupDone: String,
        val objectDetectionWarmupFailed: String,
        val objectDetectionAnnouncementTemplate: String,
        val objectDetectionNoObjects: String,
        val objectDetectionDebugTemplate: String,
        val objectDetectionTitle: String,
        val objectDetectionSummary: String,
        val objectDetectionStatus: String,
        val switchedToSceneDescriptionMode: String,
        val switchedToObjectDetectionMode: String,
        val switchedToCurrencyMode: String
    ) {
        fun initialUiState(): CameraUiState = CameraUiState(
            activeMode = CameraMode.OBJECT_DETECTION,
            ocrGuidanceMessage = ocrGuidanceSearch,
            title = objectDetectionTitle,
            summary = objectDetectionSummary,
            statusMessage = objectDetectionStatus,
            lastAnnouncement = initialAnnouncement,
            debugMetrics = initialDebug
        )

        fun translateTitle(value: String, target: CameraText): String = when (value) {
            ocrTitle -> target.ocrTitle
            objectDetectionTitle -> target.objectDetectionTitle
            sceneDescriptionTitle -> target.sceneDescriptionTitle
            currencyTitle -> target.currencyTitle
            else -> value
        }

        fun translateSummary(value: String, target: CameraText): String = when (value) {
            ocrSummary -> target.ocrSummary
            objectDetectionSummary -> target.objectDetectionSummary
            sceneDescriptionSummary -> target.sceneDescriptionSummary
            currencySummary -> target.currencySummary
            else -> value
        }

        fun translateStatus(value: String, target: CameraText): String = translateKnownRuntimeText(value, target)

        fun translateAnnouncement(value: String, target: CameraText): String = translateKnownRuntimeText(value, target)

        fun translateOcrGuidance(value: String, target: CameraText): String = when (value) {
            ocrGuidanceSearch -> target.ocrGuidanceSearch
            ocrGuidanceTooDark -> target.ocrGuidanceTooDark
            ocrGuidanceTooBright -> target.ocrGuidanceTooBright
            ocrGuidanceMoveCloser -> target.ocrGuidanceMoveCloser
            ocrGuidanceMoveBack -> target.ocrGuidanceMoveBack
            ocrGuidanceTextClipped -> target.ocrGuidanceTextClipped
            ocrGuidanceMoveLeft -> target.ocrGuidanceMoveLeft
            ocrGuidanceMoveRight -> target.ocrGuidanceMoveRight
            ocrGuidanceMoveUp -> target.ocrGuidanceMoveUp
            ocrGuidanceMoveDown -> target.ocrGuidanceMoveDown
            ocrGuidanceReady -> target.ocrGuidanceReady
            ocrGuidanceHoldSteady -> target.ocrGuidanceHoldSteady
            processingOcrImage -> target.processingOcrImage
            processingCurrencyImage -> target.processingCurrencyImage
            else -> value
        }

        fun ocrGuidanceText(): OcrGuidanceText = OcrGuidanceText(
            searching = ocrGuidanceSearch,
            tooDark = ocrGuidanceTooDark,
            tooBright = ocrGuidanceTooBright,
            moveCloser = ocrGuidanceMoveCloser,
            moveBack = ocrGuidanceMoveBack,
            textClipped = ocrGuidanceTextClipped,
            moveLeft = ocrGuidanceMoveLeft,
            moveRight = ocrGuidanceMoveRight,
            moveUp = ocrGuidanceMoveUp,
            moveDown = ocrGuidanceMoveDown,
            ready = ocrGuidanceReady,
            holdSteady = ocrGuidanceHoldSteady
        )

        fun ocrDocumentStrings(): RecognizeOcrDocumentStrings = RecognizeOcrDocumentStrings(
            gptRefusedReason = gptRefusedReason,
            apiKeyReason = apiKeyReason,
            modelPermissionReason = modelPermissionReason,
            quotaReason = quotaReason,
            timeoutReason = timeoutReason,
            unknownReason = unknownReason,
            unknownError = unknownError,
            translationUnchangedReason = translationUnchangedReason
        )

        fun translateDebug(value: String, target: CameraText): String = when (value) {
            initialDebug -> target.initialDebug
            ocrDebugWaiting -> target.ocrDebugWaiting
            currencyDebugWaiting -> target.currencyDebugWaiting
            else -> value
        }

        private fun translateKnownRuntimeText(value: String, target: CameraText): String = when (value) {
            initialStatus -> target.initialStatus
            initialAnnouncement -> target.initialAnnouncement
            waitingForClearTextFrame -> target.waitingForClearTextFrame
            waitingForSceneFrame -> target.waitingForSceneFrame
            waitingForClearMoneyImage -> target.waitingForClearMoneyImage
            currencyInstruction -> target.currencyInstruction
            currencyModelLoadError -> target.currencyModelLoadError
            currencyModelInitError -> target.currencyModelInitError
            frameUnclearRetrying -> target.frameUnclearRetrying
            cannotProcessCapturedImage -> target.cannotProcessCapturedImage
            processingOcrImage -> target.processingOcrImage
            processingCurrencyImage -> target.processingCurrencyImage
            cannotCaptureOcrTryAgain -> target.cannotCaptureOcrTryAgain
            cannotCaptureCurrencyTryAgain -> target.cannotCaptureCurrencyTryAgain
            cannotCaptureSceneTryAgain -> target.cannotCaptureSceneTryAgain
            readyToCaptureOcr -> target.readyToCaptureOcr
            readyForNewCapture -> target.readyForNewCapture
            switchedToReadTextMode -> target.switchedToReadTextMode
            describingScenePleaseWait -> target.describingScenePleaseWait
            sceneDescriptionDone -> target.sceneDescriptionDone
            sceneDescriptionFailed -> target.sceneDescriptionFailed
            sceneDescriptionErrorApiKeyMissing -> target.sceneDescriptionErrorApiKeyMissing
            sceneDescriptionErrorUnauthorized -> target.sceneDescriptionErrorUnauthorized
            sceneDescriptionErrorQuota -> target.sceneDescriptionErrorQuota
            sceneDescriptionErrorTimeout -> target.sceneDescriptionErrorTimeout
            sceneDescriptionErrorGeneric -> target.sceneDescriptionErrorGeneric
            noTextDetectedTryAgain -> target.noTextDetectedTryAgain
            updatingOcrReading -> target.updatingOcrReading
            objectDetectionWarmupDone -> target.objectDetectionWarmupDone
            objectDetectionWarmupFailed -> target.objectDetectionWarmupFailed
            objectDetectionStatus -> target.objectDetectionStatus
            switchedToSceneDescriptionMode -> target.switchedToSceneDescriptionMode
            switchedToObjectDetectionMode -> target.switchedToObjectDetectionMode
            switchedToCurrencyMode -> target.switchedToCurrencyMode
            else -> value
        }

        fun ocrFailed(reason: String): String = ocrFailedTemplate.format(reason)

        fun sceneDescriptionError(error: SceneDescriptionError): String = when (error) {
            SceneDescriptionError.API_KEY_MISSING -> sceneDescriptionErrorApiKeyMissing
            SceneDescriptionError.UNAUTHORIZED -> sceneDescriptionErrorUnauthorized
            SceneDescriptionError.RATE_LIMIT -> sceneDescriptionErrorQuota
            SceneDescriptionError.TIMEOUT -> sceneDescriptionErrorTimeout
            SceneDescriptionError.OFFLINE,
            SceneDescriptionError.EMPTY_RESPONSE,
            SceneDescriptionError.UNKNOWN -> sceneDescriptionErrorGeneric
        }

        fun accuracyOcrFallback(reason: String): String = accuracyOcrFallbackTemplate.format(reason)

        fun ocrModeLabel(mode: OcrMode): String = when (mode) {
            OcrMode.QUICK -> quickModeLabel
            OcrMode.ACCURACY -> accurateModeLabel
        }

        fun switchedToOcrMode(modeLabel: String): String = switchedToOcrModeTemplate.format(modeLabel)

        fun switchedOcrMode(mode: OcrMode): String = if (mode == OcrMode.QUICK) {
            switchedToQuickMode
        } else {
            switchedToAccurateMode
        }

        fun gptFallbackStatus(count: Int): String = gptFallbackStatusTemplate.format(count)

        fun capturedParagraphs(count: Int): String = capturedParagraphsTemplate.format(count)

        fun ocrSentencePosition(index: Int, total: Int, sentence: String): String =
            ocrSentencePositionTemplate.format(index, total, sentence)

        fun objectDetectionAnnouncement(label: String, position: String): String =
            objectDetectionAnnouncementTemplate.format(label, position)

        fun objectDetectionDebug(count: Int): String = objectDetectionDebugTemplate.format(count)

        fun currencyDetectedStatus(display: String, confidence: String): String =
            currencyDetectedStatusTemplate.format(display, confidence)

        fun currencyDebug(confidence: String): String = currencyDebugTemplate.format(confidence)

        companion object {
            fun from(localizedTextProvider: LocalizedTextProvider, language: AppLanguage): CameraText {
                val resources = localizedTextProvider.localizedContext(language).resources
                return CameraText(
                    ocrGuidanceSearch = resources.getString(R.string.camera_vm_ocr_guidance_search),
                    ocrGuidanceTooDark = resources.getString(R.string.camera_vm_ocr_guidance_too_dark),
                    ocrGuidanceTooBright = resources.getString(R.string.camera_vm_ocr_guidance_too_bright),
                    ocrGuidanceMoveCloser = resources.getString(R.string.camera_vm_ocr_guidance_move_closer),
                    ocrGuidanceMoveBack = resources.getString(R.string.camera_vm_ocr_guidance_move_back),
                    ocrGuidanceTextClipped = resources.getString(R.string.camera_vm_ocr_guidance_text_clipped),
                    ocrGuidanceMoveLeft = resources.getString(R.string.camera_vm_ocr_guidance_move_left),
                    ocrGuidanceMoveRight = resources.getString(R.string.camera_vm_ocr_guidance_move_right),
                    ocrGuidanceMoveUp = resources.getString(R.string.camera_vm_ocr_guidance_move_up),
                    ocrGuidanceMoveDown = resources.getString(R.string.camera_vm_ocr_guidance_move_down),
                    ocrGuidanceReady = resources.getString(R.string.camera_vm_ocr_guidance_ready),
                    ocrGuidanceHoldSteady = resources.getString(R.string.camera_vm_ocr_guidance_hold_steady),
                    initialStatus = resources.getString(R.string.camera_vm_initial_status),
                    initialAnnouncement = resources.getString(R.string.camera_vm_initial_announcement),
                    initialDebug = resources.getString(R.string.camera_vm_initial_debug),
                    ocrTitle = resources.getString(R.string.camera_vm_ocr_title),
                    ocrSummary = resources.getString(R.string.camera_vm_ocr_summary),
                    sceneDescriptionTitle = resources.getString(R.string.camera_vm_scene_description_title),
                    sceneDescriptionSummary = resources.getString(R.string.camera_vm_scene_description_summary),
                    currencyTitle = resources.getString(R.string.camera_vm_currency_title),
                    currencySummary = resources.getString(R.string.camera_vm_currency_summary),
                    currencyInstruction = resources.getString(R.string.currency_instruction),
                    currencyModelLoadError = resources.getString(R.string.currency_model_load_error),
                    currencyModelInitError = resources.getString(R.string.currency_model_init_error),
                    currencyDetectedStatusTemplate = resources.getString(R.string.currency_detected_status),
                    currencyDebugTemplate = resources.getString(R.string.camera_vm_currency_debug_template),
                    currencyDebugWaiting = resources.getString(R.string.camera_vm_currency_debug_waiting),
                    noCurrencyDetected = resources.getString(R.string.camera_vm_no_currency_detected),
                    waitingForClearTextFrame = resources.getString(R.string.camera_vm_waiting_for_clear_text_frame),
                    waitingForSceneFrame = resources.getString(R.string.camera_vm_waiting_for_scene_frame),
                    waitingForClearMoneyImage = resources.getString(R.string.camera_vm_waiting_for_clear_money_image),
                    frameUnclearRetrying = resources.getString(R.string.camera_vm_frame_unclear_retrying),
                    cannotProcessCapturedImage = resources.getString(R.string.camera_vm_cannot_process_captured_image),
                    processingOcrImage = resources.getString(R.string.camera_vm_processing_ocr_image),
                    processingCurrencyImage = resources.getString(R.string.camera_vm_processing_currency_image),
                    unknownReason = resources.getString(R.string.camera_vm_unknown_reason),
                    unknownError = resources.getString(R.string.camera_vm_unknown_error),
                    cannotCaptureOcrTryAgain = resources.getString(R.string.camera_vm_cannot_capture_ocr_try_again),
                    cannotCaptureCurrencyTryAgain = resources.getString(R.string.camera_vm_cannot_capture_currency_try_again),
                    imageMayBeUnclearCapturing = resources.getString(R.string.camera_vm_image_may_be_unclear_capturing),
                    readyToCaptureOcr = resources.getString(R.string.camera_vm_ready_to_capture_ocr),
                    readyForNewCapture = resources.getString(R.string.camera_vm_ready_for_new_capture),
                    endOfText = resources.getString(R.string.camera_vm_end_of_text),
                    startOfText = resources.getString(R.string.camera_vm_start_of_text),
                    switchedToReadTextMode = resources.getString(R.string.camera_vm_switched_to_read_text_mode),
                    ocrDebugWaiting = resources.getString(R.string.camera_vm_ocr_debug_waiting),
                    enabledEnglishToVietnameseTranslation = resources.getString(R.string.camera_vm_enabled_english_to_vietnamese_translation),
                    disabledEnglishToVietnameseTranslation = resources.getString(R.string.camera_vm_disabled_english_to_vietnamese_translation),
                    cannotCaptureSceneTryAgain = resources.getString(R.string.camera_vm_cannot_capture_scene_try_again),
                    describingScenePleaseWait = resources.getString(R.string.camera_vm_describing_scene_please_wait),
                    sceneDescriptionDone = resources.getString(R.string.camera_vm_scene_description_done),
                    sceneDescriptionFailed = resources.getString(R.string.camera_vm_scene_description_failed),
                    sceneDescriptionErrorApiKeyMissing = resources.getString(R.string.scene_description_error_api_key_missing),
                    sceneDescriptionErrorUnauthorized = resources.getString(R.string.scene_description_error_unauthorized),
                    sceneDescriptionErrorQuota = resources.getString(R.string.scene_description_error_quota),
                    sceneDescriptionErrorTimeout = resources.getString(R.string.scene_description_error_timeout),
                    sceneDescriptionErrorGeneric = resources.getString(R.string.scene_description_error_generic),
                    sceneDescriptionOfflineFallback = resources.getString(R.string.scene_description_offline_fallback),
                    gptRefusedReason = resources.getString(R.string.camera_vm_gpt_refused_reason),
                    noTextDetectedTryAgain = resources.getString(R.string.camera_vm_no_text_detected_try_again),
                    updatingOcrReading = resources.getString(R.string.camera_vm_updating_ocr_reading),
                    apiKeyReason = resources.getString(R.string.camera_vm_api_key_reason),
                    modelPermissionReason = resources.getString(R.string.camera_vm_model_permission_reason),
                    quotaReason = resources.getString(R.string.camera_vm_quota_reason),
                    timeoutReason = resources.getString(R.string.camera_vm_timeout_reason),
                    translationUnchangedReason = resources.getString(R.string.camera_vm_translation_unchanged_reason),
                    cannotTranslateReadingOriginal = resources.getString(R.string.camera_vm_cannot_translate_reading_original),
                    ocrFailedTemplate = resources.getString(R.string.camera_vm_ocr_failed_template),
                    accuracyOcrFallbackTemplate = resources.getString(R.string.camera_vm_accuracy_ocr_fallback_template),
                    quickModeLabel = resources.getString(R.string.camera_vm_quick_mode_label),
                    accurateModeLabel = resources.getString(R.string.camera_vm_accurate_mode_label),
                    switchedToOcrModeTemplate = resources.getString(R.string.camera_vm_switched_to_ocr_mode_template),
                    switchedToQuickMode = resources.getString(R.string.camera_vm_switched_to_quick_mode),
                    switchedToAccurateMode = resources.getString(R.string.camera_vm_switched_to_accurate_mode),
                    gptFallbackStatusTemplate = resources.getString(R.string.camera_vm_gpt_fallback_status_template),
                    capturedParagraphsTemplate = resources.getString(R.string.camera_vm_captured_paragraphs_template),
                    ocrSentencePositionTemplate = resources.getString(R.string.camera_vm_ocr_sentence_position_template),
                    objectDetectionWarmupDone = resources.getString(R.string.camera_vm_object_detection_warmup_done),
                    objectDetectionWarmupFailed = resources.getString(R.string.camera_vm_object_detection_warmup_failed),
                    objectDetectionAnnouncementTemplate = resources.getString(R.string.camera_vm_object_detection_announcement_template),
                    objectDetectionNoObjects = resources.getString(R.string.camera_vm_object_detection_no_objects),
                    objectDetectionDebugTemplate = resources.getString(R.string.camera_vm_object_detection_debug_template),
                    objectDetectionTitle = resources.getString(R.string.camera_vm_object_detection_title),
                    objectDetectionSummary = resources.getString(R.string.camera_vm_object_detection_summary),
                    objectDetectionStatus = resources.getString(R.string.camera_vm_object_detection_status),
                    switchedToSceneDescriptionMode = resources.getString(R.string.camera_vm_switched_to_scene_description_mode),
                    switchedToObjectDetectionMode = resources.getString(R.string.camera_vm_switched_to_object_detection_mode),
                    switchedToCurrencyMode = resources.getString(R.string.camera_vm_switched_to_currency_mode)
                )
            }
        }
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
