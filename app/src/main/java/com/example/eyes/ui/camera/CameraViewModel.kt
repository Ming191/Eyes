package com.example.eyes.ui.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.ai.DepthHazardDetector
import com.example.eyes.ai.DepthHazardSnapshot
import com.example.eyes.ai.DepthMap
import com.example.eyes.ai.Detection
import com.example.eyes.ai.HazardAlertPipeline
import com.example.eyes.ai.HazardFusionEngine
import com.example.eyes.ai.MiDasDepthEstimator
import com.example.eyes.ai.YoloDetector
import com.example.eyes.ai.Zone
import com.example.eyes.camera.toBitmapWithRotation
import com.example.eyes.data.DataStoreManager
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.ocr.MlKitOcrGuidanceAnalyzer
import com.example.eyes.ocr.OcrEngine
import com.example.eyes.ocr.OcrGuidanceEvaluator
import com.example.eyes.ocr.OcrGuidanceStatus
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult
import com.example.eyes.ocr.OcrTextBounds
import com.example.eyes.ocr.OcrTranslator
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.system.TtsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val activeMode: CameraMode = CameraMode.OBSTACLE,
    val ocrMode: OcrMode = OcrMode.QUICK,
    val ocrTranslateToVietnamese: Boolean = false,
    val canTranslateCurrentOcrDocument: Boolean = false,
    val isOcrScanning: Boolean = false,
    val ocrSentences: List<String> = emptyList(),
    val ocrCurrentIndex: Int = 0,
    val ocrCapturedBitmap: Bitmap? = null,
    val ocrGuidanceStatus: OcrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
    val ocrGuidanceMessage: String = "Hãy hướng camera vào vùng văn bản.",
    val isOcrReadyToCapture: Boolean = false,
    val ocrGuidanceBounds: OcrTextBounds? = null,
    val title: String = "Chế độ phát hiện vật cản",
    val summary: String = "Ứng dụng đang theo dõi vật cản liên tục. Nhấn giữ màn hình để mô tả cảnh xung quanh.",
    val statusMessage: String = "Đang chờ khung hình tiếp theo",
    val lastAnnouncement: String = "Chưa có cảnh báo mới",
    val debugMetrics: String = "Debug: đang chờ dữ liệu",
    val depthPreviewBitmap: Bitmap? = null,
    val isDescribingScene: Boolean = false,
    val isStatusCardVisible: Boolean = true,
    val boundingBoxes: List<BoundingBoxUi> = emptyList()
) {
    val isOcrDocumentMode: Boolean get() = ocrSentences.isNotEmpty()
    val currentOcrSentence: String get() = ocrSentences.getOrElse(ocrCurrentIndex) { "" }
    val hasNextOcrSentence: Boolean get() = ocrCurrentIndex < ocrSentences.lastIndex
    val hasPrevOcrSentence: Boolean get() = ocrCurrentIndex > 0
}

@Immutable
data class BoundingBoxUi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val labelVi: String,
    val zoneLabel: String,
    val confidence: Float
)

@Immutable
enum class CameraMode(
    val labelVi: String,
    val descriptionVi: String
) {
    OBSTACLE("Vật cản", "phát hiện vật cản"),
    OCR("Đọc văn bản", "đọc văn bản OCR")
}

private data class OcrRecognitionOutcome(
    val result: Result<OcrResult>,
    val usedFallbackFromAccuracy: Boolean,
    val fallbackReason: String? = null
)

class CameraViewModel(
    private val yoloDetector: YoloDetector,
    private val miDasDepthEstimator: MiDasDepthEstimator,
    private val quickOcrEngine: OcrEngine,
    private val accuracyOcrEngine: OcrEngine,
    private val ocrGuidanceAnalyzer: MlKitOcrGuidanceAnalyzer,
    private val translator: OcrTranslator,
    private val ttsService: TtsService,
    private val hapticService: HapticService,
    private val dataStoreManager: DataStoreManager,
    private val sceneRepository: SceneRepository,
    private val audioManager: AudioManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraText.forLanguage(AppLanguage.VI).initialUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private val isProcessingOcrGuidance = AtomicBoolean(false)
    private val isDepthUpdating = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)
    private val lastOcrSwipeAtMs = AtomicReference(0L)
    private val lastOcrGuidanceAtMs = AtomicLong(0L)
    private val lastOcrGuidanceSpeechAtMs = AtomicLong(0L)

    private val depthHazardDetector = DepthHazardDetector()
    private val currentOcrMode = MutableStateFlow(OcrMode.QUICK)

    private val latestDepthMap = AtomicReference<DepthMap?>(null)
    private val latestDepthHazardSnapshot = AtomicReference(DepthHazardSnapshot(hazard = null, atMs = 0L))
    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val latestDetections = AtomicReference<List<Detection>>(emptyList())
    private val hazardAlertPipeline = HazardAlertPipeline(
        hazardFusionEngine = HazardFusionEngine(),
        latestDepthHazardSnapshot = { latestDepthHazardSnapshot.get() },
        isHeadsetConnected = { isHeadsetConnected() },
        dispatchHaptic = ::dispatchObstacleHaptic,
        speakUrgent = { announcement -> ttsService.speak(announcement, SpeechOutput.Priority.URGENT) }
    )

    private val alertSensitivity = MutableStateFlow(HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY)
    private val appLanguage = AtomicReference(AppLanguage.VI)
    private val lastRawOcrResult = AtomicReference<OcrResult?>(null)
    private val lastOcrUsedFallback = AtomicBoolean(false)
    private val lastOcrGuidanceBounds = AtomicReference<OcrTextBounds?>(null)
    private val stableOcrGuidanceFrames = AtomicInteger(0)
    private val lastAnnouncedOcrGuidanceStatus = AtomicReference<OcrGuidanceStatus?>(null)
    private var reprocessOcrJob: Job? = null
    private val cameraText: CameraText get() = CameraText.forLanguage(appLanguage.get())

    init {
        viewModelScope.launch {
            dataStoreManager.appLanguageFlow.collect { language -> appLanguage.set(language) }
        }
        viewModelScope.launch {
            dataStoreManager.alertSensitivityFlow.collect { alertSensitivity.value = it }
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
            dataStoreManager.lastVoiceCommandFlow.collect { command ->
                when (command) {
                    VoiceCommand.ReadText -> applyVoiceCameraCommand(
                        mode = CameraMode.OCR,
                        statusMessage = cameraText.waitingForClearTextFrame
                    )

                    VoiceCommand.DescribeScene -> applyVoiceCameraCommand(
                        mode = CameraMode.OBSTACLE,
                        title = cameraText.sceneDescriptionTitle,
                        summary = cameraText.sceneDescriptionSummary,
                        statusMessage = cameraText.waitingForSceneFrame
                    )

                    VoiceCommand.RecognizeCurrency -> applyVoiceCameraCommand(
                        mode = CameraMode.OCR,
                        title = cameraText.currencyTitle,
                        summary = cameraText.currencySummary,
                        statusMessage = cameraText.waitingForClearMoneyImage
                    )

                    VoiceCommand.DetectObstacle -> applyVoiceCameraCommand(
                        mode = CameraMode.OBSTACLE,
                        statusMessage = cameraText.trackingObstaclesAhead
                    )

                    else -> return@collect
                }
                dataStoreManager.clearLastVoiceCommand()
            }
        }
    }

    private fun applyVoiceCameraCommand(
        mode: CameraMode,
        title: String = if (mode == CameraMode.OCR) cameraText.ocrTitle else cameraText.obstacleTitle,
        summary: String = if (mode == CameraMode.OCR) cameraText.ocrSummary else cameraText.obstacleSummary,
        statusMessage: String
    ) {
        _uiState.update {
            it.copy(
                activeMode = mode,
                title = title,
                summary = summary,
                statusMessage = statusMessage,
                lastAnnouncement = statusMessage,
                isOcrScanning = false,
                ocrSentences = emptyList(),
                ocrCurrentIndex = 0,
                canTranslateCurrentOcrDocument = false,
                ocrCapturedBitmap = null,
                ocrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
                ocrGuidanceMessage = cameraText.ocrGuidanceSearch,
                isOcrReadyToCapture = false,
                ocrGuidanceBounds = null,
                boundingBoxes = if (mode == CameraMode.OCR) emptyList() else it.boundingBoxes,
                depthPreviewBitmap = if (mode == CameraMode.OCR) null else it.depthPreviewBitmap
            )
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        when (_uiState.value.activeMode) {
            CameraMode.OBSTACLE -> processObstacleImageProxy(imageProxy)
            CameraMode.OCR -> processOcrGuidanceImageProxy(imageProxy)
        }
    }

    private fun processObstacleImageProxy(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = imageProxy.toBitmapWithRotation()
                latestFrame.set(bitmap)
                processObstacleFrame(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(statusMessage = cameraText.frameUnclearRetrying) }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
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
                val frame = ocrGuidanceAnalyzer.analyze(bitmap)
                val stableCount = updateOcrGuidanceStability(frame.textBounds)
                val evaluation = OcrGuidanceEvaluator.evaluate(frame, stableCount, appLanguage.get())
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
                it.copy(
                    isOcrScanning = true,
                    ocrSentences = emptyList(),
                    ocrCurrentIndex = 0,
                    canTranslateCurrentOcrDocument = false,
                    ocrCapturedBitmap = bitmap,
                    ocrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
                    ocrGuidanceMessage = cameraText.processingOcrImage,
                    isOcrReadyToCapture = false,
                    ocrGuidanceBounds = null,
                    statusMessage = cameraText.processingOcrImage
                )
            }

            val outcome = recognizeByMode(bitmap = bitmap, mode = currentOcrMode.value)
            val result = outcome.result.getOrElse { error ->
                _uiState.update {
                    it.copy(isOcrScanning = false, statusMessage = cameraText.ocrFailed(error.message ?: cameraText.unknownReason))
                }
                hapticService.error()
                return@launch
            }
            lastRawOcrResult.set(result)
            lastOcrUsedFallback.set(outcome.usedFallbackFromAccuracy)
            val canTranslateDocument = looksEnglish(result.fullText)
            val translatedResult = maybeTranslateForSpeech(result)
            if (outcome.usedFallbackFromAccuracy) {
                dataStoreManager.setOcrMode(OcrMode.QUICK)
                val reason = outcome.fallbackReason ?: cameraText.unknownError
                ttsService.speak(
                    cameraText.accuracyOcrFallback(reason),
                    TtsService.Priority.URGENT
                )
            }
            enterOcrDocumentMode(
                result = translatedResult,
                usedFallback = outcome.usedFallbackFromAccuracy,
                canTranslateCurrentDocument = canTranslateDocument
            )
        }
    }

    fun onOcrCaptureError() {
        _uiState.update { it.copy(isOcrScanning = false, statusMessage = cameraText.cannotCaptureOcrTryAgain) }
        hapticService.error()
    }

    fun onOcrCaptureRequested() {
        if (!_uiState.value.isOcrReadyToCapture) {
            ttsService.speak(cameraText.imageMayBeUnclearCapturing, TtsService.Priority.HIGH)
        }
    }

    fun prepareForNextOcrCapture() {
        lastRawOcrResult.set(null)
        lastOcrUsedFallback.set(false)
        resetOcrGuidanceTracking()
        _uiState.update {
            it.copy(
                isOcrScanning = false,
                ocrSentences = emptyList(),
                ocrCurrentIndex = 0,
                canTranslateCurrentOcrDocument = false,
                ocrCapturedBitmap = null,
                ocrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
                ocrGuidanceMessage = cameraText.ocrGuidanceSearch,
                isOcrReadyToCapture = false,
                ocrGuidanceBounds = null,
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
            ttsService.speak(cameraText.endOfText, TtsService.Priority.URGENT)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex + 1) }
        speakCurrentOcrSentence()
    }

    fun prevOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasPrevOcrSentence) {
            ttsService.speak(cameraText.startOfText, TtsService.Priority.URGENT)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex - 1) }
        speakCurrentOcrSentence()
    }

    fun selectMode(mode: CameraMode) {
        if (_uiState.value.activeMode == mode) return
        resetOcrGuidanceTracking()
        when (mode) {
            CameraMode.OBSTACLE -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    ttsService.speak(cameraText.switchedToObstacleMode, TtsService.Priority.HIGH)
                }
                _uiState.update {
                    it.copy(
                        activeMode = CameraMode.OBSTACLE,
                        title = cameraText.obstacleTitle,
                        summary = cameraText.obstacleSummary,
                        statusMessage = cameraText.scanningObstacles,
                        lastAnnouncement = cameraText.switchedToObstacleMode,
                        isOcrScanning = false,
                        ocrSentences = emptyList(),
                        ocrCurrentIndex = 0,
                        canTranslateCurrentOcrDocument = false,
                        ocrCapturedBitmap = null,
                        ocrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
                        ocrGuidanceMessage = cameraText.ocrGuidanceSearch,
                        isOcrReadyToCapture = false,
                        ocrGuidanceBounds = null
                    )
                }
            }
            CameraMode.OCR -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    val modeLabel = cameraText.ocrModeLabel(currentOcrMode.value)
                    ttsService.speak(cameraText.switchedToOcrMode(modeLabel), TtsService.Priority.HIGH)
                }
                ttsService.warmupLocale(Locale.US)
                latestDetections.set(emptyList())
                latestDepthMap.set(null)
                latestDepthHazardSnapshot.set(DepthHazardSnapshot(hazard = null, atMs = 0L))
                hazardAlertPipeline.resetSafeStatus()
                updateUiStateAndRecycleReplacedDepthPreview {
                    it.copy(
                        activeMode = CameraMode.OCR,
                        title = cameraText.ocrTitle,
                        summary = cameraText.ocrSummary,
                        statusMessage = cameraText.readyToCaptureOcr,
                        lastAnnouncement = cameraText.switchedToReadTextMode,
                        isOcrScanning = false,
                        ocrSentences = emptyList(),
                        ocrCurrentIndex = 0,
                        canTranslateCurrentOcrDocument = false,
                        ocrCapturedBitmap = null,
                        ocrGuidanceStatus = OcrGuidanceStatus.SEARCHING,
                        ocrGuidanceMessage = cameraText.ocrGuidanceSearch,
                        isOcrReadyToCapture = false,
                        ocrGuidanceBounds = null,
                        boundingBoxes = emptyList(),
                        depthPreviewBitmap = null,
                        debugMetrics = cameraText.ocrDebugWaiting
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
                TtsService.Priority.NORMAL
            )
        }
    }

    fun setOcrTranslateToVietnamese(enabled: Boolean) {
        reprocessOcrJob?.cancel()
        reprocessOcrJob = viewModelScope.launch {
            dataStoreManager.setOcrTranslateToVietnamese(enabled)
            ttsService.speak(
                if (enabled) {
                    cameraText.enabledEnglishToVietnameseTranslation
                } else {
                    cameraText.disabledEnglishToVietnameseTranslation
                },
                TtsService.Priority.NORMAL
            )
            reprocessCurrentOcrForTranslationToggle(enabled)
        }
    }

    fun describeScene() {
        val currentFrame = latestFrame.get() ?: run {
            _uiState.update { it.copy(statusMessage = cameraText.noFrameToDescribe) }
            hapticService.error()
            return
        }
        if (_uiState.value.isDescribingScene) return
        _uiState.update { it.copy(isDescribingScene = true, statusMessage = cameraText.describingScenePleaseWait) }
        hapticService.loading()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val description = sceneRepository.describeScene(bitmap = currentFrame, detections = latestDetections.get())
                if (!isHeadsetConnected()) {
                    ttsService.speak(description, TtsService.Priority.HIGH)
                }
                hapticService.confirm()
                _uiState.update { it.copy(statusMessage = cameraText.sceneDescriptionDone, lastAnnouncement = description) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Describe scene failed", e)
                _uiState.update { it.copy(statusMessage = cameraText.sceneDescriptionFailed) }
                hapticService.error()
            } finally {
                _uiState.update { it.copy(isDescribingScene = false) }
            }
        }
    }

    fun toggleStatusCardVisibility() {
        _uiState.update { it.copy(isStatusCardVisible = !it.isStatusCardVisible) }
    }

    fun onScreenDisposed() {
        if (_uiState.value.activeMode == CameraMode.OCR) {
            ttsService.stop()
        }
        lastRawOcrResult.set(null)
        lastOcrUsedFallback.set(false)
        resetOcrGuidanceTracking()
        updateUiStateAndRecycleReplacedOcrBitmap { it.copy(ocrCapturedBitmap = null) }
    }

    override fun onCleared() {
        yoloDetector.close()
        quickOcrEngine.close()
        accuracyOcrEngine.close()
        ocrGuidanceAnalyzer.close()
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
        val now = System.currentTimeMillis()
        val previousStatus = lastAnnouncedOcrGuidanceStatus.get()
        val elapsed = now - lastOcrGuidanceSpeechAtMs.get()
        if (
            status != OcrGuidanceStatus.READY &&
            elapsed < OCR_GUIDANCE_SPEECH_INTERVAL_MS
        ) {
            return
        }
        if (previousStatus == status && elapsed < OCR_GUIDANCE_SPEECH_INTERVAL_MS) return

        lastAnnouncedOcrGuidanceStatus.set(status)
        lastOcrGuidanceSpeechAtMs.set(now)

        if (status == OcrGuidanceStatus.READY && previousStatus != OcrGuidanceStatus.READY) {
            hapticService.confirm()
            ttsService.speak(message, TtsService.Priority.HIGH)
        } else if (status != OcrGuidanceStatus.READY) {
            ttsService.speak(message, TtsService.Priority.NORMAL)
        }
    }

    private fun resetOcrGuidanceTracking() {
        lastOcrGuidanceBounds.set(null)
        stableOcrGuidanceFrames.set(0)
        lastAnnouncedOcrGuidanceStatus.set(null)
        lastOcrGuidanceSpeechAtMs.set(0L)
        lastOcrGuidanceAtMs.set(0L)
    }

    private fun OcrTextBounds.isStableComparedTo(other: OcrTextBounds): Boolean {
        val centerDelta = kotlin.math.abs(centerX - other.centerX) + kotlin.math.abs(centerY - other.centerY)
        val areaDelta = kotlin.math.abs(area - other.area)
        return centerDelta < OCR_GUIDANCE_STABLE_CENTER_DELTA && areaDelta < OCR_GUIDANCE_STABLE_AREA_DELTA
    }

    private fun processObstacleFrame(bitmap: Bitmap) {
        if (_uiState.value.activeMode != CameraMode.OBSTACLE) return
        maybeRefreshDepth(bitmap)
        val detections = yoloDetector.detect(bitmap)
        latestDetections.set(detections)

        val depthMap = latestDepthMap.get()
        if (depthMap != null) {
            detections.forEach { it.midasDepth = miDasDepthEstimator.depthAt(depthMap, it.bbox) }
        }

        _uiState.update {
            it.copy(
                boundingBoxes = detections
                    .sortedByDescending { d -> d.confidence }
                    .take(MAX_OVERLAY_BOXES)
                    .map { d ->
                        BoundingBoxUi(
                            left = d.bbox.left,
                            top = d.bbox.top,
                            right = d.bbox.right,
                            bottom = d.bbox.bottom,
                            labelVi = d.labelVi,
                            zoneLabel = d.zone.labelVi,
                            confidence = d.confidence
                        )
                    }
            )
        }
        handleObstacleAlert(detections)
    }

    private suspend fun recognizeByMode(bitmap: Bitmap, mode: OcrMode): OcrRecognitionOutcome {
        return when (mode) {
            OcrMode.QUICK -> OcrRecognitionOutcome(
                result = runCatching { quickOcrEngine.recognize(bitmap) },
                usedFallbackFromAccuracy = false
            )
            OcrMode.ACCURACY -> {
                val accuracyResult = runCatching { accuracyOcrEngine.recognize(bitmap) }
                val text = accuracyResult.getOrNull()?.fullText.orEmpty()
                val refused = accuracyResult.isSuccess && looksLikeGptRefusal(text)
                if (accuracyResult.isSuccess && !refused) {
                    OcrRecognitionOutcome(
                        result = accuracyResult,
                        usedFallbackFromAccuracy = false
                    )
                } else {
                    val reason = when {
                        refused -> cameraText.gptRefusedReason
                        accuracyResult.exceptionOrNull() != null -> buildFallbackReason(accuracyResult.exceptionOrNull()!!)
                        else -> cameraText.unknownReason
                    }
                    OcrRecognitionOutcome(
                        result = runCatching { quickOcrEngine.recognize(bitmap) },
                        usedFallbackFromAccuracy = true,
                        fallbackReason = reason
                    )
                }
            }
        }
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

    private suspend fun maybeTranslateForSpeech(result: OcrResult): OcrResult {
        val enabled = _uiState.value.ocrTranslateToVietnamese
        if (!enabled) return OcrPostProcessor.process(result.fullText)
        if (!shouldAutoTranslateToVietnamese(result.fullText)) return OcrPostProcessor.process(result.fullText)
        return translateToVietnameseOrFallback(result.fullText)
    }

    private suspend fun reprocessCurrentOcrForTranslationToggle(enabled: Boolean) {
        val state = _uiState.value
        val rawResult = lastRawOcrResult.get() ?: return
        if (state.activeMode != CameraMode.OCR || !state.isOcrDocumentMode) return

        _uiState.update { it.copy(statusMessage = cameraText.updatingOcrReading) }
        val processed = if (enabled && shouldAutoTranslateToVietnamese(rawResult.fullText)) {
            translateToVietnameseOrFallback(rawResult.fullText)
        } else {
            OcrPostProcessor.process(rawResult.fullText)
        }
        enterOcrDocumentMode(
            result = processed,
            usedFallback = lastOcrUsedFallback.get(),
            canTranslateCurrentDocument = shouldAutoTranslateToVietnamese(rawResult.fullText)
        )
    }

    private fun speakCurrentOcrSentence() {
        val state = _uiState.value
        if (!state.isOcrDocumentMode) return
        val sentence = state.currentOcrSentence
        val locale = if (looksEnglish(sentence)) Locale.US else VIETNAMESE_LOCALE
        ttsService.speak(
            cameraText.ocrSentencePosition(state.ocrCurrentIndex + 1, state.ocrSentences.size, sentence),
            TtsService.Priority.URGENT,
            locale
        )
    }

    private fun looksLikeGptRefusal(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return true
        val refusalMarkers = listOf(
            "i'm sorry, i can't assist with that",
            "i can't assist with that",
            "i cannot assist with that",
            "i'm sorry",
            "i cannot help with that request"
        )
        return refusalMarkers.any { normalized.startsWith(it) }
    }

    private fun buildFallbackReason(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        return when {
            message.contains("401") -> cameraText.apiKeyReason
            message.contains("403") -> cameraText.modelPermissionReason
            message.contains("429") -> cameraText.quotaReason
            message.contains("timeout", ignoreCase = true) -> cameraText.timeoutReason
            message.isNotBlank() -> message
            else -> error::class.simpleName ?: cameraText.unknownError
        }
    }

    private fun looksEnglish(text: String): Boolean {
        if (text.isBlank()) return false
        if (VI_DIACRITIC_REGEX.containsMatchIn(text)) return false
        return EN_LETTER_REGEX.containsMatchIn(text)
    }

    private fun shouldAutoTranslateToVietnamese(text: String): Boolean {
        if (text.isBlank()) return false
        val latinCount = EN_LETTER_REGEX.findAll(text).count()
        if (latinCount < 6) return false
        val viCount = VI_DIACRITIC_REGEX.findAll(text).count()
        return viCount == 0 || latinCount >= viCount * 3
    }

    private suspend fun translateToVietnameseOrFallback(sourceText: String): OcrResult {
        return runCatching {
            val translated = translator.translateToVietnamese(sourceText)
            if (looksUntranslated(sourceText, translated)) {
                throw IllegalStateException(cameraText.translationUnchangedReason)
            }
            OcrPostProcessor.process(translated)
        }.getOrElse {
            ttsService.speak(
                cameraText.cannotTranslateReadingOriginal,
                TtsService.Priority.HIGH
            )
            OcrPostProcessor.process(sourceText)
        }
    }

    private fun looksUntranslated(source: String, translated: String): Boolean {
        val sourceNorm = OcrPostProcessor.normalizeText(source).lowercase()
        val translatedNorm = OcrPostProcessor.normalizeText(translated).lowercase()
        if (sourceNorm.isBlank() || translatedNorm.isBlank()) return true

        val sourceWordCount = sourceNorm.split(Regex("\\s+")).count { it.isNotBlank() }
        if (sourceWordCount < 3) return false

        val similarity = OcrPostProcessor.similarityRatio(sourceNorm, translatedNorm)
        return similarity >= 0.92f && !VI_DIACRITIC_REGEX.containsMatchIn(translatedNorm)
    }

    private fun canHandleOcrSwipe(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastOcrSwipeAtMs.get()
        if (now - last < OCR_SWIPE_DEBOUNCE_MS) return false
        lastOcrSwipeAtMs.set(now)
        return true
    }

    private fun maybeRefreshDepth(bitmap: Bitmap) {
        val currentFrameIndex = frameCounter.incrementAndGet()
        if (currentFrameIndex % DEPTH_FRAME_INTERVAL != 0) return
        if (!isDepthUpdating.compareAndSet(false, true)) return
        val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val newMap = miDasDepthEstimator.estimateDepth(snapshot)
                latestDepthMap.set(newMap)
                val hazard = depthHazardDetector.detect(newMap)
                latestDepthHazardSnapshot.set(
                    DepthHazardSnapshot(
                        hazard = hazard,
                        atMs = if (hazard != null) System.currentTimeMillis() else 0L
                    )
                )
                val previewBitmap = buildDepthPreviewBitmap(newMap)
                updateUiStateAndRecycleReplacedDepthPreview { it.copy(depthPreviewBitmap = previewBitmap) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Depth refresh failed", t)
            } finally {
                recycleBitmapIfNeeded(snapshot)
                isDepthUpdating.set(false)
            }
        }
    }

    private fun buildDepthPreviewBitmap(depthMap: DepthMap): Bitmap {
        val pixels = IntArray(depthMap.width * depthMap.height)
        depthMap.values.forEachIndexed { index, value ->
            val intensity = (value.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            pixels[index] = Color.argb(255, intensity, intensity, intensity)
        }
        return createBitmap(depthMap.width, depthMap.height).apply {
            setPixels(pixels, 0, depthMap.width, 0, 0, depthMap.width, depthMap.height)
        }
    }

    private fun updateUiStateAndRecycleReplacedDepthPreview(
        transform: (CameraUiState) -> CameraUiState
    ) {
        var previousPreview: Bitmap? = null
        var nextPreview: Bitmap? = null
        _uiState.update { state ->
            val updatedState = transform(state)
            previousPreview = state.depthPreviewBitmap
            nextPreview = updatedState.depthPreviewBitmap
            updatedState
        }
        if (previousPreview !== nextPreview) recycleBitmapIfNeeded(previousPreview)
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

    private fun handleObstacleAlert(detections: List<Detection>) {
        val result = hazardAlertPipeline.process(
            detections = detections,
            alertSensitivity = alertSensitivity.value,
            language = appLanguage.get()
        )
        _uiState.update {
            it.copy(
                statusMessage = result.statusMessage ?: it.statusMessage,
                lastAnnouncement = result.lastAnnouncement ?: it.lastAnnouncement,
                debugMetrics = result.debugMetrics
            )
        }
    }

    private fun dispatchObstacleHaptic(zone: Zone) {
        when (zone) {
            Zone.LEFT -> hapticService.obstacleLeft()
            Zone.CENTER -> hapticService.obstacleCenter()
            Zone.RIGHT -> hapticService.obstacleRight()
        }
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
        val obstacleTitle: String,
        val obstacleSummary: String,
        val initialStatus: String,
        val initialAnnouncement: String,
        val initialDebug: String,
        val ocrTitle: String,
        val ocrSummary: String,
        val sceneDescriptionTitle: String,
        val sceneDescriptionSummary: String,
        val currencyTitle: String,
        val currencySummary: String,
        val waitingForClearTextFrame: String,
        val waitingForSceneFrame: String,
        val waitingForClearMoneyImage: String,
        val trackingObstaclesAhead: String,
        val frameUnclearRetrying: String,
        val cannotProcessCapturedImage: String,
        val processingOcrImage: String,
        val unknownReason: String,
        val unknownError: String,
        val cannotCaptureOcrTryAgain: String,
        val imageMayBeUnclearCapturing: String,
        val readyToCaptureOcr: String,
        val readyForNewCapture: String,
        val endOfText: String,
        val startOfText: String,
        val switchedToObstacleMode: String,
        val scanningObstacles: String,
        val switchedToReadTextMode: String,
        val ocrDebugWaiting: String,
        val enabledEnglishToVietnameseTranslation: String,
        val disabledEnglishToVietnameseTranslation: String,
        val noFrameToDescribe: String,
        val describingScenePleaseWait: String,
        val sceneDescriptionDone: String,
        val sceneDescriptionFailed: String,
        val gptRefusedReason: String,
        val noTextDetectedTryAgain: String,
        val updatingOcrReading: String,
        val apiKeyReason: String,
        val modelPermissionReason: String,
        val quotaReason: String,
        val timeoutReason: String,
        val translationUnchangedReason: String,
        val cannotTranslateReadingOriginal: String
    ) {
        fun initialUiState(): CameraUiState = CameraUiState(
            ocrGuidanceMessage = ocrGuidanceSearch,
            title = obstacleTitle,
            summary = obstacleSummary,
            statusMessage = initialStatus,
            lastAnnouncement = initialAnnouncement,
            debugMetrics = initialDebug
        )

        fun ocrFailed(reason: String): String = when (this) {
            VI -> "OCR thất bại: $reason"
            EN -> "OCR failed: $reason"
            else -> "OCR failed: $reason"
        }

        fun accuracyOcrFallback(reason: String): String = when (this) {
            VI -> "OCR chính xác gặp lỗi: $reason. Đã chuyển sang OCR nhanh."
            EN -> "Accurate OCR failed: $reason. Switched to quick OCR."
            else -> "Accurate OCR failed: $reason. Switched to quick OCR."
        }

        fun ocrModeLabel(mode: OcrMode): String = when (mode) {
            OcrMode.QUICK -> if (this == VI) "Nhanh" else "Quick"
            OcrMode.ACCURACY -> if (this == VI) "Chính xác" else "Accurate"
        }

        fun switchedToOcrMode(modeLabel: String): String = when (this) {
            VI -> "Đã chuyển sang chế độ đọc văn bản, mode $modeLabel."
            EN -> "Switched to read text mode, $modeLabel mode."
            else -> "Switched to read text mode, $modeLabel mode."
        }

        fun switchedOcrMode(mode: OcrMode): String = when (this) {
            VI -> if (mode == OcrMode.QUICK) "Đã chuyển sang mode Nhanh." else "Đã chuyển sang mode Chính xác."
            EN -> if (mode == OcrMode.QUICK) "Switched to quick mode." else "Switched to accurate mode."
            else -> if (mode == OcrMode.QUICK) "Switched to quick mode." else "Switched to accurate mode."
        }

        fun gptFallbackStatus(count: Int): String = when (this) {
            VI -> "GPT-4o lỗi, đã fallback OCR nhanh. $count đoạn."
            EN -> "GPT-4o failed, fell back to quick OCR. $count sections."
            else -> "GPT-4o failed, fell back to quick OCR. $count sections."
        }

        fun capturedParagraphs(count: Int): String = when (this) {
            VI -> "Đã chụp. $count đoạn văn."
            EN -> "Captured. $count paragraphs."
            else -> "Captured. $count paragraphs."
        }

        fun ocrSentencePosition(index: Int, total: Int, sentence: String): String = when (this) {
            VI -> "$index trên $total. $sentence"
            EN -> "$index of $total. $sentence"
            else -> "$index of $total. $sentence"
        }

        companion object {
            private val VI = CameraText(
                ocrGuidanceSearch = "Hãy hướng camera vào vùng văn bản.",
                obstacleTitle = "Chế độ phát hiện vật cản",
                obstacleSummary = "Ứng dụng đang theo dõi vật cản liên tục. Nhấn giữ màn hình để mô tả cảnh xung quanh.",
                initialStatus = "Đang chờ khung hình tiếp theo",
                initialAnnouncement = "Chưa có cảnh báo mới",
                initialDebug = "Debug: đang chờ dữ liệu",
                ocrTitle = "Chế độ đọc văn bản",
                ocrSummary = "Double tap để chụp ảnh văn bản. Vuốt trái/phải để đọc từng câu.",
                sceneDescriptionTitle = "Chế độ mô tả khung cảnh",
                sceneDescriptionSummary = "Lia camera chậm để ứng dụng chuẩn bị mô tả không gian phía trước.",
                currencyTitle = "Chế độ nhận diện tiền",
                currencySummary = "Đưa tờ tiền vào giữa khung hình để ứng dụng chuẩn bị nhận diện mệnh giá.",
                waitingForClearTextFrame = "Đang chờ khung hình văn bản rõ nét",
                waitingForSceneFrame = "Đang chờ khung hình khung cảnh",
                waitingForClearMoneyImage = "Đang chờ hình ảnh tờ tiền rõ nét",
                trackingObstaclesAhead = "Đang theo dõi vật cản phía trước",
                frameUnclearRetrying = "Khung hình chưa rõ, đang thử lại",
                cannotProcessCapturedImage = "Không thể xử lý ảnh đã chụp",
                processingOcrImage = "Đang xử lý ảnh OCR",
                unknownReason = "không rõ nguyên nhân",
                unknownError = "lỗi không xác định",
                cannotCaptureOcrTryAgain = "Không thể chụp ảnh OCR. Hãy thử lại.",
                imageMayBeUnclearCapturing = "Ảnh có thể chưa rõ, đang chụp.",
                readyToCaptureOcr = "Sẵn sàng chụp OCR",
                readyForNewCapture = "Đã sẵn sàng chụp ảnh mới",
                endOfText = "Đã đến cuối văn bản.",
                startOfText = "Đây là đầu văn bản.",
                switchedToObstacleMode = "Đã chuyển sang chế độ phát hiện vật cản",
                scanningObstacles = "Đang quét vật cản",
                switchedToReadTextMode = "Đã chuyển sang chế độ đọc văn bản",
                ocrDebugWaiting = "OCR: đang chờ dữ liệu",
                enabledEnglishToVietnameseTranslation = "Đã bật dịch tiếng Anh sang tiếng Việt khi đọc.",
                disabledEnglishToVietnameseTranslation = "Đã tắt dịch tiếng Anh sang tiếng Việt.",
                noFrameToDescribe = "Chưa có khung hình để mô tả. Hãy giữ camera ổn định vài giây.",
                describingScenePleaseWait = "Đang mô tả cảnh, vui lòng chờ",
                sceneDescriptionDone = "Đã mô tả cảnh xong",
                sceneDescriptionFailed = "Mô tả cảnh thất bại, vui lòng thử lại",
                gptRefusedReason = "GPT-4o trả về nội dung từ chối",
                noTextDetectedTryAgain = "Không phát hiện văn bản. Hãy chụp lại rõ hơn.",
                updatingOcrReading = "Đang cập nhật bản đọc OCR",
                apiKeyReason = "sai API key hoặc chưa cấp quyền",
                modelPermissionReason = "không có quyền dùng model hoặc endpoint",
                quotaReason = "hết quota hoặc bị giới hạn tốc độ",
                timeoutReason = "hết thời gian chờ phản hồi",
                translationUnchangedReason = "Bản dịch không thay đổi nội dung",
                cannotTranslateReadingOriginal = "Không thể dịch sang tiếng Việt. Đang đọc bản gốc tiếng Anh."
            )

            private val EN = CameraText(
                ocrGuidanceSearch = "Point camera at text area.",
                obstacleTitle = "Obstacle detection mode",
                obstacleSummary = "App is continuously watching for obstacles. Long press screen to describe surroundings.",
                initialStatus = "Waiting for next frame",
                initialAnnouncement = "No new alerts",
                initialDebug = "Debug: waiting for data",
                ocrTitle = "Read text mode",
                ocrSummary = "Double tap to capture text. Swipe left or right to read each sentence.",
                sceneDescriptionTitle = "Scene description mode",
                sceneDescriptionSummary = "Move camera slowly so app can prepare description of space ahead.",
                currencyTitle = "Currency recognition mode",
                currencySummary = "Place bill in center of frame so app can prepare denomination recognition.",
                waitingForClearTextFrame = "Waiting for clear text frame",
                waitingForSceneFrame = "Waiting for scene frame",
                waitingForClearMoneyImage = "Waiting for clear bill image",
                trackingObstaclesAhead = "Tracking obstacles ahead",
                frameUnclearRetrying = "Frame unclear, trying again",
                cannotProcessCapturedImage = "Cannot process captured image",
                processingOcrImage = "Processing OCR image",
                unknownReason = "unknown reason",
                unknownError = "unknown error",
                cannotCaptureOcrTryAgain = "Cannot capture OCR image. Try again.",
                imageMayBeUnclearCapturing = "Image may be unclear, capturing.",
                readyToCaptureOcr = "Ready to capture OCR",
                readyForNewCapture = "Ready to capture new image",
                endOfText = "End of text.",
                startOfText = "Start of text.",
                switchedToObstacleMode = "Switched to obstacle detection mode",
                scanningObstacles = "Scanning obstacles",
                switchedToReadTextMode = "Switched to read text mode",
                ocrDebugWaiting = "OCR: waiting for data",
                enabledEnglishToVietnameseTranslation = "Enabled English to Vietnamese translation while reading.",
                disabledEnglishToVietnameseTranslation = "Disabled English to Vietnamese translation.",
                noFrameToDescribe = "No frame to describe. Hold camera steady for a few seconds.",
                describingScenePleaseWait = "Describing scene, please wait",
                sceneDescriptionDone = "Scene description done",
                sceneDescriptionFailed = "Scene description failed, please try again",
                gptRefusedReason = "GPT-4o returned refusal content",
                noTextDetectedTryAgain = "No text detected. Capture again more clearly.",
                updatingOcrReading = "Updating OCR reading",
                apiKeyReason = "invalid API key or missing permission",
                modelPermissionReason = "no permission for model or endpoint",
                quotaReason = "quota exhausted or rate limited",
                timeoutReason = "response timed out",
                translationUnchangedReason = "Translation did not change content",
                cannotTranslateReadingOriginal = "Cannot translate to Vietnamese. Reading original English."
            )

            fun forLanguage(language: AppLanguage): CameraText = when (language) {
                AppLanguage.VI -> VI
                AppLanguage.EN -> EN
            }
        }
    }

    private companion object {
        private const val TAG = "CameraViewModel"
        private const val DEPTH_FRAME_INTERVAL = 1
        private const val MAX_OVERLAY_BOXES = 8
        private const val OCR_SWIPE_DEBOUNCE_MS = 320L
        private const val OCR_GUIDANCE_INTERVAL_MS = 700L
        private const val OCR_GUIDANCE_SPEECH_INTERVAL_MS = 4_000L
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
