package com.example.eyes.ui.camera

import android.annotation.SuppressLint
import android.content.Context
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
import com.example.eyes.camera.toBitmapWithRotation
import com.example.eyes.data.DataStoreManager
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.localizedFor
import com.example.eyes.ocr.MlKitOcrGuidanceAnalyzer
import com.example.eyes.ocr.OcrEngine
import com.example.eyes.ocr.OcrGuidanceEvaluator
import com.example.eyes.ocr.OcrGuidanceStatus
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult
import com.example.eyes.ocr.OcrTextBounds
import com.example.eyes.ocr.OcrTranslator
import com.example.eyes.objectdetection.YoloExecutorchDetector
import com.example.eyes.objectdetection.localizedText
import com.example.eyes.system.HapticService
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
    val activeMode: CameraMode = CameraMode.OBJECT_DETECTION,
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
    val title: String = "Chế độ đọc văn bản",
    val summary: String = "Đưa camera vào vùng văn bản, chạm hai lần để chụp và đọc.",
    val statusMessage: String = "Đang chờ khung hình tiếp theo",
    val lastAnnouncement: String = "Chưa có cảnh báo mới",
    val debugMetrics: String = "Debug: đang chờ dữ liệu",
    val isDescribingScene: Boolean = false,
    val isStatusCardVisible: Boolean = true,
    val objectDetections: List<DetectionOverlayItem> = emptyList()
) {
    val isOcrDocumentMode: Boolean get() = ocrSentences.isNotEmpty()
    val currentOcrSentence: String get() = ocrSentences.getOrElse(ocrCurrentIndex) { "" }
    val hasNextOcrSentence: Boolean get() = ocrCurrentIndex < ocrSentences.lastIndex
    val hasPrevOcrSentence: Boolean get() = ocrCurrentIndex > 0
}

@Immutable
enum class CameraMode(
    val labelVi: String,
    val descriptionVi: String
) {
    OCR("Đọc văn bản", "đọc văn bản OCR"),
    OBJECT_DETECTION("Nhận diện vật thể", "nhận diện vật thể")
}

@Immutable
data class DetectionOverlayItem(
    val label: String,
    val confidence: Float,
    val positionText: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private data class OcrRecognitionOutcome(
    val result: Result<OcrResult>,
    val usedFallbackFromAccuracy: Boolean,
    val fallbackReason: String? = null
)

class CameraViewModel(
    private val quickOcrEngine: OcrEngine,
    private val accuracyOcrEngine: OcrEngine,
    private val ocrGuidanceAnalyzer: MlKitOcrGuidanceAnalyzer,
    private val translator: OcrTranslator,
    private val ttsService: TtsService,
    private val hapticService: HapticService,
    private val dataStoreManager: DataStoreManager,
    private val sceneRepository: SceneRepository,
    private val objectDetector: YoloExecutorchDetector,
    private val audioManager: AudioManager,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraText.from(context, AppLanguage.VI).initialUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingOcrGuidance = AtomicBoolean(false)
    private val isProcessingObjectDetection = AtomicBoolean(false)
    private val lastOcrSwipeAtMs = AtomicReference(0L)
    private val lastOcrGuidanceAtMs = AtomicLong(0L)
    private val lastOcrGuidanceSpeechAtMs = AtomicLong(0L)
    private val lastObjectDetectionAtMs = AtomicLong(0L)
    private val lastObjectAnnouncementAtMs = AtomicLong(0L)
    private val lastObjectAnnouncement = AtomicReference("")

    private val currentOcrMode = MutableStateFlow(OcrMode.QUICK)

    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val appLanguage = AtomicReference(AppLanguage.VI)
    private val lastRawOcrResult = AtomicReference<OcrResult?>(null)
    private val lastOcrUsedFallback = AtomicBoolean(false)
    private val lastOcrGuidanceBounds = AtomicReference<OcrTextBounds?>(null)
    private val stableOcrGuidanceFrames = AtomicInteger(0)
    private val lastAnnouncedOcrGuidanceStatus = AtomicReference<OcrGuidanceStatus?>(null)
    private var reprocessOcrJob: Job? = null
    private val cameraText: CameraText get() = CameraText.from(context, appLanguage.get())

    init {
        viewModelScope.launch {
            dataStoreManager.appLanguageFlow.collect { language ->
                val previousText = cameraText
                appLanguage.set(language)
                refreshLanguageBoundUiText(previousText, cameraText)
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
            dataStoreManager.lastVoiceCommandFlow.collect { command ->
                when (command) {
                    VoiceCommand.ReadText -> applyVoiceCameraCommand(
                        mode = CameraMode.OCR,
                        statusMessage = cameraText.waitingForClearTextFrame
                    )

                    VoiceCommand.DescribeScene -> applyVoiceCameraCommand(
                        mode = CameraMode.OCR,
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

                    else -> return@collect
                }
                dataStoreManager.clearLastVoiceCommand()
            }
        }
        warmUpObjectDetectionModel()
    }

    private fun warmUpObjectDetectionModel() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { objectDetector.inspectOutputShape() }
                .onSuccess { outputs ->
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
                .onFailure { error ->
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

    private fun applyVoiceCameraCommand(
        mode: CameraMode,
        title: String = cameraText.ocrTitle,
        summary: String = cameraText.ocrSummary,
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
                objectDetections = emptyList()
            )
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        when (_uiState.value.activeMode) {
            CameraMode.OCR -> processOcrGuidanceImageProxy(imageProxy)
            CameraMode.OBJECT_DETECTION -> processObjectDetectionImageProxy(imageProxy)
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

    private suspend fun processObjectDetection(bitmap: Bitmap) {
        runCatching { objectDetector.detect(bitmap) }
            .onSuccess { detections ->
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
                maybeSpeakObjectDetection(announcement, overlayItems.isNotEmpty())
                _uiState.update {
                    it.copy(
                        objectDetections = overlayItems,
                        lastAnnouncement = announcement,
                        debugMetrics = cameraText.objectDetectionDebug(overlayItems.size)
                    )
                }
            }
            .onFailure { error ->
                Log.e(TAG, "Object detection failed", error)
                _uiState.update {
                    it.copy(debugMetrics = "YOLO detect: ${error.message ?: error::class.java.simpleName}")
                }
            }
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
            priority = TtsService.Priority.NORMAL,
            locale = appLanguage.get().ttsLocale
        )
    }

    private fun com.example.eyes.objectdetection.Detection.toOverlayItem(
        frameWidth: Int,
        frameHeight: Int
    ): DetectionOverlayItem {
        val box: RectF = boundingBox
        return DetectionOverlayItem(
            label = label,
            confidence = confidence,
            positionText = position.localizedText(context, appLanguage.get()),
            left = box.left / frameWidth,
            top = box.top / frameHeight,
            right = box.right / frameWidth,
            bottom = box.bottom / frameHeight
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
                    TtsService.Priority.URGENT,
                    appLanguage.get().ttsLocale
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
            ttsService.speak(cameraText.imageMayBeUnclearCapturing, TtsService.Priority.HIGH, appLanguage.get().ttsLocale)
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
            ttsService.speak(cameraText.endOfText, TtsService.Priority.URGENT, appLanguage.get().ttsLocale)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex + 1) }
        speakCurrentOcrSentence()
    }

    fun prevOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasPrevOcrSentence) {
            ttsService.speak(cameraText.startOfText, TtsService.Priority.URGENT, appLanguage.get().ttsLocale)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex - 1) }
        speakCurrentOcrSentence()
    }

    fun selectMode(mode: CameraMode) {
        if (_uiState.value.activeMode == mode) return
        resetOcrGuidanceTracking()
        when (mode) {
            CameraMode.OCR -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    val modeLabel = cameraText.ocrModeLabel(currentOcrMode.value)
                    ttsService.speak(cameraText.switchedToOcrMode(modeLabel), TtsService.Priority.HIGH, appLanguage.get().ttsLocale)
                }
                ttsService.warmupLocale(Locale.US)
                _uiState.update {
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
                        objectDetections = emptyList(),
                        debugMetrics = cameraText.ocrDebugWaiting
                    )
                }
            }
            CameraMode.OBJECT_DETECTION -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    ttsService.speak(cameraText.switchedToObjectDetectionMode, TtsService.Priority.HIGH, appLanguage.get().ttsLocale)
                }
                _uiState.update {
                    it.copy(
                        activeMode = CameraMode.OBJECT_DETECTION,
                        title = cameraText.objectDetectionTitle,
                        summary = cameraText.objectDetectionSummary,
                        statusMessage = cameraText.objectDetectionStatus,
                        lastAnnouncement = cameraText.switchedToObjectDetectionMode,
                        isOcrScanning = false,
                        ocrSentences = emptyList(),
                        ocrCurrentIndex = 0,
                        canTranslateCurrentOcrDocument = false,
                        ocrCapturedBitmap = null,
                        ocrGuidanceBounds = null,
                        isOcrReadyToCapture = false,
                        objectDetections = emptyList(),
                        debugMetrics = cameraText.objectDetectionDebug(0)
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
                TtsService.Priority.NORMAL,
                appLanguage.get().ttsLocale
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
                TtsService.Priority.NORMAL,
                appLanguage.get().ttsLocale
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
                val description = sceneRepository.describeScene(bitmap = currentFrame)
                if (!isHeadsetConnected()) {
                    ttsService.speak(description, TtsService.Priority.HIGH, appLanguage.get().ttsLocale)
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
            ttsService.speak(message, TtsService.Priority.HIGH, appLanguage.get().ttsLocale)
        } else if (status != OcrGuidanceStatus.READY) {
            ttsService.speak(message, TtsService.Priority.NORMAL, appLanguage.get().ttsLocale)
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
                TtsService.Priority.HIGH,
                appLanguage.get().ttsLocale
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
        val switchedToObjectDetectionMode: String
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
            processingOcrImage -> target.processingOcrImage
            else -> value
        }

        fun translateDebug(value: String, target: CameraText): String = when (value) {
            initialDebug -> target.initialDebug
            ocrDebugWaiting -> target.ocrDebugWaiting
            else -> value
        }

        private fun translateKnownRuntimeText(value: String, target: CameraText): String = when (value) {
            initialStatus -> target.initialStatus
            initialAnnouncement -> target.initialAnnouncement
            waitingForClearTextFrame -> target.waitingForClearTextFrame
            waitingForSceneFrame -> target.waitingForSceneFrame
            waitingForClearMoneyImage -> target.waitingForClearMoneyImage
            frameUnclearRetrying -> target.frameUnclearRetrying
            cannotProcessCapturedImage -> target.cannotProcessCapturedImage
            processingOcrImage -> target.processingOcrImage
            cannotCaptureOcrTryAgain -> target.cannotCaptureOcrTryAgain
            readyToCaptureOcr -> target.readyToCaptureOcr
            readyForNewCapture -> target.readyForNewCapture
            switchedToReadTextMode -> target.switchedToReadTextMode
            noFrameToDescribe -> target.noFrameToDescribe
            describingScenePleaseWait -> target.describingScenePleaseWait
            sceneDescriptionDone -> target.sceneDescriptionDone
            sceneDescriptionFailed -> target.sceneDescriptionFailed
            noTextDetectedTryAgain -> target.noTextDetectedTryAgain
            updatingOcrReading -> target.updatingOcrReading
            objectDetectionWarmupDone -> target.objectDetectionWarmupDone
            objectDetectionWarmupFailed -> target.objectDetectionWarmupFailed
            objectDetectionStatus -> target.objectDetectionStatus
            switchedToObjectDetectionMode -> target.switchedToObjectDetectionMode
            else -> value
        }

        fun ocrFailed(reason: String): String = ocrFailedTemplate.format(reason)

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

        companion object {
            fun from(context: Context, language: AppLanguage): CameraText {
                val resources = context.localizedFor(language).resources
                return CameraText(
                    ocrGuidanceSearch = resources.getString(R.string.camera_vm_ocr_guidance_search),
                    initialStatus = resources.getString(R.string.camera_vm_initial_status),
                    initialAnnouncement = resources.getString(R.string.camera_vm_initial_announcement),
                    initialDebug = resources.getString(R.string.camera_vm_initial_debug),
                    ocrTitle = resources.getString(R.string.camera_vm_ocr_title),
                    ocrSummary = resources.getString(R.string.camera_vm_ocr_summary),
                    sceneDescriptionTitle = resources.getString(R.string.camera_vm_scene_description_title),
                    sceneDescriptionSummary = resources.getString(R.string.camera_vm_scene_description_summary),
                    currencyTitle = resources.getString(R.string.camera_vm_currency_title),
                    currencySummary = resources.getString(R.string.camera_vm_currency_summary),
                    waitingForClearTextFrame = resources.getString(R.string.camera_vm_waiting_for_clear_text_frame),
                    waitingForSceneFrame = resources.getString(R.string.camera_vm_waiting_for_scene_frame),
                    waitingForClearMoneyImage = resources.getString(R.string.camera_vm_waiting_for_clear_money_image),
                    frameUnclearRetrying = resources.getString(R.string.camera_vm_frame_unclear_retrying),
                    cannotProcessCapturedImage = resources.getString(R.string.camera_vm_cannot_process_captured_image),
                    processingOcrImage = resources.getString(R.string.camera_vm_processing_ocr_image),
                    unknownReason = resources.getString(R.string.camera_vm_unknown_reason),
                    unknownError = resources.getString(R.string.camera_vm_unknown_error),
                    cannotCaptureOcrTryAgain = resources.getString(R.string.camera_vm_cannot_capture_ocr_try_again),
                    imageMayBeUnclearCapturing = resources.getString(R.string.camera_vm_image_may_be_unclear_capturing),
                    readyToCaptureOcr = resources.getString(R.string.camera_vm_ready_to_capture_ocr),
                    readyForNewCapture = resources.getString(R.string.camera_vm_ready_for_new_capture),
                    endOfText = resources.getString(R.string.camera_vm_end_of_text),
                    startOfText = resources.getString(R.string.camera_vm_start_of_text),
                    switchedToReadTextMode = resources.getString(R.string.camera_vm_switched_to_read_text_mode),
                    ocrDebugWaiting = resources.getString(R.string.camera_vm_ocr_debug_waiting),
                    enabledEnglishToVietnameseTranslation = resources.getString(R.string.camera_vm_enabled_english_to_vietnamese_translation),
                    disabledEnglishToVietnameseTranslation = resources.getString(R.string.camera_vm_disabled_english_to_vietnamese_translation),
                    noFrameToDescribe = resources.getString(R.string.camera_vm_no_frame_to_describe),
                    describingScenePleaseWait = resources.getString(R.string.camera_vm_describing_scene_please_wait),
                    sceneDescriptionDone = resources.getString(R.string.camera_vm_scene_description_done),
                    sceneDescriptionFailed = resources.getString(R.string.camera_vm_scene_description_failed),
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
                    switchedToObjectDetectionMode = resources.getString(R.string.camera_vm_switched_to_object_detection_mode)
                )
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
        private const val OBJECT_DETECTION_INTERVAL_MS = 1_000L
        private const val OBJECT_ANNOUNCEMENT_INTERVAL_MS = 3_000L
        private const val OBJECT_ANNOUNCEMENT_REPEAT_MS = 6_000L
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
