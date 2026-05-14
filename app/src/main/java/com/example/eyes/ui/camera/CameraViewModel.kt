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
import com.example.eyes.ocr.OcrEngine
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult
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
import java.util.concurrent.atomic.AtomicReference

@Immutable
data class CameraUiState(
    val activeMode: CameraMode = CameraMode.OBSTACLE,
    val ocrMode: OcrMode = OcrMode.QUICK,
    val isOcrScanning: Boolean = false,
    val ocrSentences: List<String> = emptyList(),
    val ocrCurrentIndex: Int = 0,
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
    OCR("Đọc chữ", "đọc chữ OCR")
}

private data class OcrRecognitionOutcome(
    val result: Result<OcrResult>,
    val usedFallbackFromAccuracy: Boolean
)

class CameraViewModel(
    private val yoloDetector: YoloDetector,
    private val miDasDepthEstimator: MiDasDepthEstimator,
    private val quickOcrEngine: OcrEngine,
    private val accuracyOcrEngine: OcrEngine,
    private val ttsService: TtsService,
    private val hapticService: HapticService,
    private val dataStoreManager: DataStoreManager,
    private val sceneRepository: SceneRepository,
    private val audioManager: AudioManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private val isDepthUpdating = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)
    private val lastOcrSwipeAtMs = AtomicReference(0L)

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
        speakUrgent = { announcement -> ttsService.speak(announcement, TtsService.Priority.URGENT) }
    )

    private val alertSensitivity = MutableStateFlow(HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY)

    init {
        viewModelScope.launch {
            dataStoreManager.alertSensitivityFlow.collect { alertSensitivity.value = it }
        }
        viewModelScope.launch {
            dataStoreManager.ocrModeFlow.collect { mode ->
                currentOcrMode.value = mode
                _uiState.update { it.copy(ocrMode = mode) }
            }
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = imageProxy.toBitmapWithRotation()
                latestFrame.set(bitmap)
                if (_uiState.value.activeMode == CameraMode.OBSTACLE) {
                    processObstacleFrame(bitmap)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(statusMessage = "Khung hình chưa rõ, đang thử lại") }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    fun processCapturedOcrImage(imageProxy: ImageProxy) {
        _uiState.update { it.copy(isOcrScanning = true, statusMessage = "Đang xử lý ảnh OCR") }
        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = try {
                imageProxy.toBitmapWithRotation()
            } catch (_: Throwable) {
                _uiState.update { it.copy(isOcrScanning = false, statusMessage = "Không thể xử lý ảnh đã chụp") }
                hapticService.error()
                return@launch
            } finally {
                imageProxy.close()
            }

            val outcome = recognizeByMode(bitmap = bitmap, mode = currentOcrMode.value)
            val result = outcome.result.getOrElse { error ->
                _uiState.update {
                    it.copy(isOcrScanning = false, statusMessage = "OCR thất bại: ${error.message ?: "không rõ nguyên nhân"}")
                }
                hapticService.error()
                return@launch
            }
            enterOcrDocumentMode(OcrPostProcessor.process(result.fullText), outcome.usedFallbackFromAccuracy)
        }
    }

    fun onOcrCaptureError() {
        _uiState.update { it.copy(isOcrScanning = false, statusMessage = "Không thể chụp ảnh OCR. Hãy thử lại.") }
        hapticService.error()
    }

    fun nextOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasNextOcrSentence) {
            ttsService.speak("Đã đến cuối văn bản.", TtsService.Priority.URGENT)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex + 1) }
        speakCurrentOcrSentence()
    }

    fun prevOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = _uiState.value
        if (!state.hasPrevOcrSentence) {
            ttsService.speak("Đây là đầu văn bản.", TtsService.Priority.URGENT)
            return
        }
        _uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex - 1) }
        speakCurrentOcrSentence()
    }

    fun selectMode(mode: CameraMode) {
        if (_uiState.value.activeMode == mode) return
        when (mode) {
            CameraMode.OBSTACLE -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    ttsService.speak("Đã chuyển sang chế độ phát hiện vật cản", TtsService.Priority.HIGH)
                }
                _uiState.update {
                    it.copy(
                        activeMode = CameraMode.OBSTACLE,
                        title = "Chế độ phát hiện vật cản",
                        summary = "Ứng dụng đang theo dõi vật cản liên tục. Nhấn giữ màn hình để mô tả cảnh xung quanh.",
                        statusMessage = "Đang quét vật cản",
                        lastAnnouncement = "Đã chuyển sang chế độ phát hiện vật cản",
                        isOcrScanning = false,
                        ocrSentences = emptyList(),
                        ocrCurrentIndex = 0
                    )
                }
            }
            CameraMode.OCR -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    ttsService.speak("Đã chuyển sang chế độ đọc chữ", TtsService.Priority.HIGH)
                }
                latestDetections.set(emptyList())
                latestDepthMap.set(null)
                latestDepthHazardSnapshot.set(DepthHazardSnapshot(hazard = null, atMs = 0L))
                hazardAlertPipeline.resetSafeStatus()
                updateUiStateAndRecycleReplacedDepthPreview {
                    it.copy(
                        activeMode = CameraMode.OCR,
                        title = "Chế độ đọc chữ",
                        summary = "Double tap để chụp ảnh văn bản. Vuốt trái/phải để đọc từng câu.",
                        statusMessage = "Sẵn sàng chụp OCR",
                        lastAnnouncement = "Đã chuyển sang chế độ đọc chữ",
                        isOcrScanning = false,
                        ocrSentences = emptyList(),
                        ocrCurrentIndex = 0,
                        boundingBoxes = emptyList(),
                        depthPreviewBitmap = null,
                        debugMetrics = "OCR: đang chờ dữ liệu"
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
                if (mode == OcrMode.QUICK) "Đã chuyển OCR nhanh bằng ML Kit" else "Đã chuyển OCR chính xác bằng GPT-4o",
                TtsService.Priority.NORMAL
            )
        }
    }

    fun describeScene() {
        val currentFrame = latestFrame.get() ?: run {
            _uiState.update { it.copy(statusMessage = "Chưa có khung hình để mô tả. Hãy giữ camera ổn định vài giây.") }
            hapticService.error()
            return
        }
        if (_uiState.value.isDescribingScene) return
        _uiState.update { it.copy(isDescribingScene = true, statusMessage = "Đang mô tả cảnh, vui lòng chờ") }
        hapticService.loading()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val description = sceneRepository.describeScene(bitmap = currentFrame, detections = latestDetections.get())
                if (!isHeadsetConnected()) {
                    ttsService.speak(description, TtsService.Priority.HIGH)
                }
                hapticService.confirm()
                _uiState.update { it.copy(statusMessage = "Đã mô tả cảnh xong", lastAnnouncement = description) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Describe scene failed", e)
                _uiState.update { it.copy(statusMessage = "Mô tả cảnh thất bại, vui lòng thử lại") }
                hapticService.error()
            } finally {
                _uiState.update { it.copy(isDescribingScene = false) }
            }
        }
    }

    fun toggleStatusCardVisibility() {
        _uiState.update { it.copy(isStatusCardVisible = !it.isStatusCardVisible) }
    }

    override fun onCleared() {
        yoloDetector.close()
        quickOcrEngine.close()
        accuracyOcrEngine.close()
        super.onCleared()
    }

    private fun processObstacleFrame(bitmap: Bitmap) {
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
            OcrMode.QUICK -> OcrRecognitionOutcome(runCatching { quickOcrEngine.recognize(bitmap) }, false)
            OcrMode.ACCURACY -> {
                val accuracyResult = runCatching { accuracyOcrEngine.recognize(bitmap) }
                val text = accuracyResult.getOrNull()?.fullText.orEmpty()
                val refused = accuracyResult.isSuccess && looksLikeGptRefusal(text)
                if (accuracyResult.isSuccess && !refused) {
                    OcrRecognitionOutcome(accuracyResult, false)
                } else {
                    OcrRecognitionOutcome(runCatching { quickOcrEngine.recognize(bitmap) }, true)
                }
            }
        }
    }

    private fun enterOcrDocumentMode(result: OcrResult, usedFallback: Boolean) {
        val sentences = if (result.sentences.isNotEmpty()) result.sentences else OcrPostProcessor.splitToSentences(result.fullText)
        val finalSentences = sentences.ifEmpty { listOf(result.fullText.trim()).filter { it.isNotBlank() } }

        if (finalSentences.isEmpty()) {
            _uiState.update { it.copy(isOcrScanning = false, statusMessage = "Không phát hiện văn bản. Hãy chụp lại rõ hơn.") }
            hapticService.error()
            return
        }

        _uiState.update {
            it.copy(
                isOcrScanning = false,
                ocrSentences = finalSentences,
                ocrCurrentIndex = 0,
                statusMessage = if (usedFallback) {
                    "GPT-4o lỗi, đã fallback OCR nhanh. ${finalSentences.size} đoạn."
                } else {
                    "Đã chụp. ${finalSentences.size} đoạn văn."
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
            "${state.ocrCurrentIndex + 1} trên ${state.ocrSentences.size}. $sentence",
            TtsService.Priority.URGENT,
            locale
        )
    }

    private fun looksLikeGptRefusal(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return true
        val refusalMarkers = listOf(
            "i'm sorry, i can't assist with that",
            "i can’t assist with that",
            "i cannot assist with that",
            "i'm sorry",
            "i cannot help with that request"
        )
        return refusalMarkers.any { normalized.startsWith(it) }
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

    private fun recycleBitmapIfNeeded(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }

    private fun handleObstacleAlert(detections: List<Detection>) {
        val result = hazardAlertPipeline.process(detections = detections, alertSensitivity = alertSensitivity.value)
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

    private companion object {
        private const val TAG = "CameraViewModel"
        private const val DEPTH_FRAME_INTERVAL = 1
        private const val MAX_OVERLAY_BOXES = 8
        private const val OCR_SWIPE_DEBOUNCE_MS = 320L
        private val VIETNAMESE_LOCALE: Locale = Locale.Builder().setLanguage("vi").setRegion("VN").build()
        private val VI_DIACRITIC_REGEX = Regex(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            RegexOption.IGNORE_CASE
        )
        private val EN_LETTER_REGEX = Regex("[A-Za-z]")
    }
}
