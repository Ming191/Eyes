package com.example.eyes.ui.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.ai.AlertSource
import com.example.eyes.ai.DepthMap
import com.example.eyes.ai.DepthHazard
import com.example.eyes.ai.DepthHazardDetector
import com.example.eyes.ai.Detection
import com.example.eyes.ai.HazardFusionEngine
import com.example.eyes.ai.HazardSeverity
import com.example.eyes.ai.MiDasDepthEstimator
import com.example.eyes.ai.SpeechRateLimiter
import com.example.eyes.ai.Zone
import com.example.eyes.ai.YoloDetector
import com.example.eyes.camera.toBitmapWithRotation
import com.example.eyes.data.DataStoreManager
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import androidx.core.graphics.createBitmap

@Immutable
data class CameraUiState(
    val activeMode: CameraMode = CameraMode.OBSTACLE,
    val title: String = "Chế độ phát hiện vật cản",
    val summary: String = "Ứng dụng đang theo dõi vật cản liên tục. Nhấn giữ màn hình để mô tả cảnh xung quanh.",
    val statusMessage: String = "Đang chờ khung hình tiếp theo",
    val lastAnnouncement: String = "Chưa có cảnh báo mới",
    val debugMetrics: String = "Debug: đang chờ dữ liệu",
    val depthPreviewBitmap: Bitmap? = null,
    val isDescribingScene: Boolean = false,
    val isStatusCardVisible: Boolean = true,
    val boundingBoxes: List<BoundingBoxUi> = emptyList()
)

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
    OBSTACLE(
        labelVi = "Vật cản",
        descriptionVi = "phát hiện vật cản"
    ),
    OCR(
        labelVi = "Đọc chữ",
        descriptionVi = "đọc chữ OCR"
    )
}

class CameraViewModel(
    private val yoloDetector: YoloDetector,
    private val miDasDepthEstimator: MiDasDepthEstimator,
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
    private val depthHazardDetector = DepthHazardDetector()
    private val hazardFusionEngine = HazardFusionEngine()
    private val speechRateLimiter = SpeechRateLimiter(cooldownMs = SPEECH_COOLDOWN_MS)
    private val latestDepthHazardAtMs = AtomicLong(0L)
    private var noHazardStreak: Int = 0
    private var lastHapticAtMs: Long = 0L

    private val latestDepthMap = AtomicReference<DepthMap?>(null)
    private val latestDepthHazard = AtomicReference<DepthHazard?>(null)
    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val latestDetections = AtomicReference<List<Detection>>(emptyList())

    private val alertSensitivity = MutableStateFlow(DEFAULT_ALERT_SENSITIVITY)

    init {
        viewModelScope.launch {
            dataStoreManager.alertSensitivityFlow.collect { value ->
                alertSensitivity.value = value
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

                when (_uiState.value.activeMode) {
                    CameraMode.OBSTACLE -> processObstacleFrame(bitmap)
                    CameraMode.OCR -> processOcrFrameStub()
                }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(statusMessage = "Khung hình chưa rõ, đang thử lại")
                }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    fun selectMode(mode: CameraMode) {
        val current = _uiState.value.activeMode
        if (mode == current) return

        when (mode) {
            CameraMode.OBSTACLE -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    ttsService.speak(
                        "Đã chuyển sang chế độ phát hiện vật cản",
                        TtsService.Priority.HIGH
                    )
                }
                _uiState.update {
                    it.copy(
                        activeMode = CameraMode.OBSTACLE,
                        title = "Chế độ phát hiện vật cản",
                        summary = "Ứng dụng đang theo dõi vật cản liên tục. Nhấn giữ màn hình để mô tả cảnh xung quanh.",
                        statusMessage = "Đang quét vật cản",
                        lastAnnouncement = "Đã chuyển sang chế độ phát hiện vật cản"
                    )
                }
            }

            CameraMode.OCR -> {
                hapticService.confirm()
                if (!isHeadsetConnected()) {
                    ttsService.speak(
                        "Đã chuyển sang chế độ đọc chữ. Tính năng này đang được phát triển",
                        TtsService.Priority.HIGH
                    )
                }
                latestDetections.set(emptyList())
                latestDepthMap.set(null)
                latestDepthHazard.set(null)
                latestDepthHazardAtMs.set(0L)
                noHazardStreak = 0
                _uiState.update {
                    it.copy(
                        activeMode = CameraMode.OCR,
                        title = "Chế độ đọc chữ",
                        summary = "Đọc chữ từ camera. Tính năng đang được phát triển.",
                        statusMessage = "TODO: Chưa triển khai OCR",
                        lastAnnouncement = "Đã chuyển sang chế độ đọc chữ",
                        boundingBoxes = emptyList(),
                        depthPreviewBitmap = null,
                        debugMetrics = "OCR TODO: chưa có dữ liệu phân tích"
                    )
                }
            }
        }
    }

    fun describeScene() {
        val currentFrame = latestFrame.get() ?: run {
            _uiState.update {
                it.copy(statusMessage = "Chưa có khung hình để mô tả. Hãy giữ camera ổn định vài giây.")
            }
            hapticService.error()
            return
        }

        if (_uiState.value.isDescribingScene) return

        _uiState.update {
            it.copy(
                isDescribingScene = true,
                statusMessage = "Đang mô tả cảnh, vui lòng chờ"
            )
        }
        hapticService.loading()

        viewModelScope.launch(Dispatchers.Default) {
            val description = sceneRepository.describeScene(
                bitmap = currentFrame,
                detections = latestDetections.get()
            )

            if (!isHeadsetConnected()) {
                ttsService.speak(description, TtsService.Priority.HIGH)
            }
            hapticService.confirm()

            _uiState.update {
                it.copy(
                    statusMessage = "Đã mô tả cảnh xong",
                    lastAnnouncement = description,
                    isDescribingScene = false
                )
            }
        }
    }

    fun toggleStatusCardVisibility() {
        _uiState.update { state ->
            state.copy(isStatusCardVisible = !state.isStatusCardVisible)
        }
    }

    private fun processObstacleFrame(bitmap: Bitmap) {
        maybeRefreshDepth(bitmap)

        val detections = yoloDetector.detect(bitmap)
        latestDetections.set(detections)

        val depthMap = latestDepthMap.get()
        if (depthMap != null) {
            detections.forEach { detection ->
                detection.midasDepth = miDasDepthEstimator.depthAt(depthMap, detection.bbox)
            }
        }

        _uiState.update {
            it.copy(
                boundingBoxes = detections
                    .sortedByDescending { detection -> detection.confidence }
                    .take(MAX_OVERLAY_BOXES)
                    .map { detection ->
                        BoundingBoxUi(
                            left = detection.bbox.left,
                            top = detection.bbox.top,
                            right = detection.bbox.right,
                            bottom = detection.bbox.bottom,
                            labelVi = detection.labelVi,
                            zoneLabel = detection.zone.labelVi,
                            confidence = detection.confidence
                        )
                    }
            )
        }

        handleObstacleAlert(detections)
    }

    private fun processOcrFrameStub() {
        // TODO: Implement OCR frame processing pipeline.
        _uiState.update {
            it.copy(
                statusMessage = "TODO: Chưa triển khai OCR",
                boundingBoxes = emptyList(),
                depthPreviewBitmap = null
            )
        }
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
                latestDepthHazard.set(hazard)
                latestDepthHazardAtMs.set(if (hazard != null) System.currentTimeMillis() else 0L)
                val previewBitmap = buildDepthPreviewBitmap(newMap)
                _uiState.update { state ->
                    state.copy(depthPreviewBitmap = previewBitmap)
                }
            } finally {
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

        return createBitmap(depthMap.width, depthMap.height)
            .apply {
                setPixels(pixels, 0, depthMap.width, 0, 0, depthMap.width, depthMap.height)
            }
    }

    private fun handleObstacleAlert(detections: List<Detection>) {
        val yoloCandidate = detections
            .asSequence()
            .filter { it.isPriority() }
            .filter { it.isNearby(alertSensitivity.value) }
            .maxByOrNull { detection ->
                val depthScore = if (detection.midasDepth > 0f) detection.midasDepth else detection.bboxDepthScore
                (depthScore * 0.7f) + (detection.confidence * 0.3f)
            }
        val yoloCompositeScore = yoloCandidate?.let { detection ->
            val depthScore = if (detection.midasDepth > 0f) detection.midasDepth else detection.bboxDepthScore
            (depthScore * 0.7f) + (detection.confidence * 0.3f)
        }

        val nowMs = System.currentTimeMillis()
        val depthCandidate = getFreshDepthCandidate(nowMs)
        val fusedAlert = hazardFusionEngine.fuse(yoloCandidate, depthCandidate)
        val headsetConnected = isHeadsetConnected()
        var speechSpoken = false

        if (fusedAlert == null) {
            noHazardStreak = (noHazardStreak + 1).coerceAtMost(SAFE_STATUS_STREAK_FRAMES + 1)
            _uiState.update {
                it.copy(
                    statusMessage = if (noHazardStreak >= SAFE_STATUS_STREAK_FRAMES) {
                        "Lối đi tạm ổn, tiếp tục quét môi trường"
                    } else {
                        it.statusMessage
                    },
                    debugMetrics = buildDebugMetrics(
                        yoloCandidate = yoloCandidate,
                        yoloCompositeScore = yoloCompositeScore,
                        depthCandidate = depthCandidate,
                        fusedAlert = null,
                        speechSpoken = false,
                        speechSuppressedByHeadset = headsetConnected,
                        sensitivity = alertSensitivity.value
                    )
                )
            }
            return
        }

        noHazardStreak = 0

        if (shouldTriggerHaptic(nowMs)) {
            when (fusedAlert.primaryZone) {
                Zone.LEFT -> hapticService.obstacleLeft()
                Zone.CENTER -> hapticService.obstacleCenter()
                Zone.RIGHT -> hapticService.obstacleRight()
            }

            fusedAlert.secondaryHapticZone?.let { secondaryZone ->
                when (secondaryZone) {
                    Zone.LEFT -> hapticService.obstacleLeft()
                    Zone.CENTER -> hapticService.obstacleCenter()
                    Zone.RIGHT -> hapticService.obstacleRight()
                }
            }
        }

        val announcement = when {
            fusedAlert.primarySource == AlertSource.YOLO && yoloCandidate != null -> {
                "Chú ý! ${yoloCandidate.labelVi} ở ${yoloCandidate.zone.labelVi}."
            }
            else -> fusedAlert.speechText ?: "Chú ý! Có vật cản gần ${fusedAlert.primaryZone.labelVi}."
        }

        if (!headsetConnected && speechRateLimiter.shouldSpeak(nowMs)) {
            ttsService.speak(announcement, TtsService.Priority.URGENT)
            speechRateLimiter.record(nowMs)
            speechSpoken = true
        }

        _uiState.update {
            it.copy(
                statusMessage = when (fusedAlert.primarySource) {
                    AlertSource.YOLO -> {
                        val label = yoloCandidate?.labelVi ?: "vật cản"
                        "Phát hiện $label ${fusedAlert.primaryZone.labelVi}"
                    }
                    AlertSource.DEPTH -> "Phát hiện vật cản gần ${fusedAlert.primaryZone.labelVi}"
                },
                lastAnnouncement = announcement,
                debugMetrics = buildDebugMetrics(
                    yoloCandidate = yoloCandidate,
                    yoloCompositeScore = yoloCompositeScore,
                    depthCandidate = depthCandidate,
                    fusedAlert = fusedAlert,
                    speechSpoken = speechSpoken,
                    speechSuppressedByHeadset = headsetConnected,
                    sensitivity = alertSensitivity.value
                )
            )
        }
    }

    private fun getFreshDepthCandidate(nowMs: Long): DepthHazard? {
        val hazardAtMs = latestDepthHazardAtMs.get()
        if (hazardAtMs <= 0L) return null
        if (nowMs - hazardAtMs > DEPTH_HAZARD_TTL_MS) return null
        return latestDepthHazard.get()
    }

    private fun shouldTriggerHaptic(nowMs: Long): Boolean {
        if (nowMs - lastHapticAtMs < HAPTIC_COOLDOWN_MS) return false
        lastHapticAtMs = nowMs
        return true
    }

    private fun buildDebugMetrics(
        yoloCandidate: Detection?,
        yoloCompositeScore: Float?,
        depthCandidate: DepthHazard?,
        fusedAlert: com.example.eyes.ai.FusedHazardAlert?,
        speechSpoken: Boolean,
        speechSuppressedByHeadset: Boolean,
        sensitivity: Float
    ): String {
        val yoloLine = if (yoloCandidate == null) {
            "YOLO: không có candidate"
        } else {
            val depthScore = if (yoloCandidate.midasDepth > 0f) yoloCandidate.midasDepth else yoloCandidate.bboxDepthScore
            "YOLO: ${yoloCandidate.labelVi} ${yoloCandidate.zone.labelVi} | conf=${fmt(yoloCandidate.confidence)} depth=${fmt(depthScore)} score=${fmt(yoloCompositeScore ?: 0f)}"
        }

        val depthLine = if (depthCandidate == null) {
            "MiDaS: không có hazard đạt ngưỡng"
        } else {
            val severityLabel = when (depthCandidate.severity) {
                HazardSeverity.HIGH -> "HIGH"
                HazardSeverity.MEDIUM -> "MEDIUM"
            }
            "MiDaS: ${depthCandidate.zone.labelVi} ${depthCandidate.band.name} | severity=$severityLabel score=${fmt(depthCandidate.score)}"
        }

        val fusionLine = if (fusedAlert == null) {
            "Fusion: không cảnh báo"
        } else {
            val secondaryLabel = fusedAlert.secondaryHapticZone?.labelVi ?: "không"
            "Fusion: primary=${fusedAlert.primarySource.name} ${fusedAlert.primaryZone.labelVi} | secondary=$secondaryLabel"
        }

        val speechLine = "Speech: spoken=$speechSpoken headset=$speechSuppressedByHeadset"
        val configLine = "Cfg: sensitivity=${fmt(sensitivity)}"

        return listOf(yoloLine, depthLine, fusionLine, speechLine, configLine).joinToString("\n")
    }

    @SuppressLint("DefaultLocale")
    private fun fmt(value: Float): String = String.format("%.2f", value)

    @SuppressLint("ObsoleteSdkInt")
    private fun isHeadsetConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { device ->
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
        private const val DEPTH_FRAME_INTERVAL = 1
        private const val DEPTH_HAZARD_TTL_MS = 2_800L
        private const val HAPTIC_COOLDOWN_MS = 300L
        private const val SPEECH_COOLDOWN_MS = 1_300L
        private const val SAFE_STATUS_STREAK_FRAMES = 2
        private const val DEFAULT_ALERT_SENSITIVITY = 0.5f
        private const val MAX_OVERLAY_BOXES = 8
    }
}
