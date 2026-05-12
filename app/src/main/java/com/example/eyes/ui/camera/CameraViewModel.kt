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

    /**
     * Processes a single camera frame according to the current camera mode and updates view state.
     *
     * This function converts the provided ImageProxy to a rotated bitmap, saves it as the latest frame,
     * and dispatches mode-specific processing (obstacle detection or OCR) on a background dispatcher.
     * If a frame is already being processed, the incoming frame is closed and ignored. The provided
     * ImageProxy is always closed by this function. On unexpected errors the UI state's statusMessage
     * is set to "Khung hình chưa rõ, đang thử lại".
     *
     * @param imageProxy The camera frame to process; this function will close the ImageProxy.
     */
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

    /**
     * Switches the camera's active mode and updates UI state, user feedback, and internal caches.
     *
     * When changing to OBSTACLE mode this triggers a confirmation haptic, optionally speaks a short
     * announcement if no headset is connected, and updates the UI title, summary, status message, and
     * last announcement. When changing to OCR mode this triggers a confirmation haptic, optionally
     * speaks an OCR-development announcement if no headset is connected, clears detection/depth/hazard
     * caches and streaks, and updates the UI to the OCR placeholder state (cleared overlays, null
     * depth preview, and OCR debug text).
     *
     * @param mode The camera mode to activate.
     */
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

    /**
     * Requests a natural-language description of the current camera view and publishes the result to UI and output devices.
     *
     * If a recent camera frame is not available, updates the status message and emits an error haptic, then returns.
     *
     * When a frame is available, marks the view model as describing the scene, emits a loading haptic, and invokes
     * the scene repository to generate a description from the latest frame and detections. After the description is ready
     * it emits a confirmation haptic, updates the UI (`statusMessage`, `lastAnnouncement`, `isDescribingScene`), and,
     * if no headset is connected, speaks the description via the TTS service.
     */
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

    /**
     * Toggles the status card visibility flag in the camera UI state.
     */
    fun toggleStatusCardVisibility() {
        _uiState.update { state ->
            state.copy(isStatusCardVisible = !state.isStatusCardVisible)
        }
    }

    /**
     * Process a rotation-corrected camera frame for obstacle detection, update overlay bounding boxes,
     * refresh depth-related caches, and trigger hazard fusion (haptics/TTS/UI) as needed.
     *
     * Updates internal caches (`latestDetections`, depth caches) and publishes new `boundingBoxes` to UI state,
     * then evaluates and handles fused obstacle alerts.
     *
     * @param bitmap The rotation-corrected camera frame to analyze. 
     */
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

    /**
     * Sets the UI into a placeholder OCR state indicating OCR is not yet implemented.
     *
     * Updates the status message to a TODO notice, clears bounding box overlays, and removes any depth preview.
     */
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

    /**
     * Periodically starts a background depth estimation for the provided camera frame and publishes its results to the ViewModel state.
     *
     * If a depth update is not scheduled for this frame or another depth update is already in progress, this function returns without effect.
     *
     * Side effects:
     * - Launches a coroutine that estimates a depth map and detects depth hazards.
     * - Updates `latestDepthMap`, `latestDepthHazard`, and `latestDepthHazardAtMs`.
     * - Updates the UI state's `depthPreviewBitmap`.
     */
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

    /**
     * Creates a grayscale ARGB preview bitmap from a depth map.
     *
     * Each depth value is clamped to [0, 1] and mapped to an 8-bit grayscale intensity
     * (0 = black, 255 = white), then written into an ARGB bitmap sized to the depth map.
     *
     * @param depthMap The source depth map containing width, height, and normalized depth values.
     * @return A bitmap where each pixel encodes the corresponding depth as a grayscale ARGB color.
     */
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

    /**
     * Compute a fused obstacle alert from the given detections, update UI state accordingly,
     * and trigger haptic and TTS notifications when appropriate.
     *
     * Selects a best YOLO candidate (nearby alert candidate scored by depth and confidence),
     * obtains a fresh depth-based candidate, fuses them via the hazard fusion engine, and:
     * - if no fused alert is produced, increments the safe-streak and updates status/debug text;
     * - if a fused alert is produced, resets the safe-streak, optionally emits haptics (with cooldown),
     *   optionally speaks an announcement (suppressed when a headset is connected and rate-limited),
     *   and updates `statusMessage`, `lastAnnouncement`, and `debugMetrics` in the UI state.
     *
     * @param detections The list of detections from the current frame to evaluate as alert candidates.
     */
    private fun handleObstacleAlert(detections: List<Detection>) {
        val yoloCandidate = detections
            .asSequence()
            .filter { it.isAlertCandidate() }
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
        val depthLabelCandidate = depthCandidate?.let { findReliableLabelForDepth(detections, it.zone) }
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
            fusedAlert.primarySource == AlertSource.DEPTH && depthLabelCandidate != null -> {
                "Chú ý! ${depthLabelCandidate.labelVi} gần ${fusedAlert.primaryZone.labelVi}."
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
                    AlertSource.DEPTH -> {
                        val label = depthLabelCandidate?.labelVi ?: "vật cản"
                        "Phát hiện $label gần ${fusedAlert.primaryZone.labelVi}"
                    }
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

    /**
     * Selects the most confident detection inside a given zone that has a reliable label.
     *
     * @param detections The list of detections to search.
     * @param zone The zone to filter detections by.
     * @return The detection in `zone` with a reliable label and the highest confidence, or `null` if none exist.
     */
    private fun findReliableLabelForDepth(detections: List<Detection>, zone: Zone): Detection? {
        return detections
            .asSequence()
            .filter { it.zone == zone }
            .filter { it.hasReliableLabel() }
            .maxByOrNull { it.confidence }
    }

    /**
     * Retrieves the most recent depth-based hazard if it exists and is still within the freshness window.
     *
     * @param nowMs Current timestamp in milliseconds used to determine freshness against DEPTH_HAZARD_TTL_MS.
     * @return The latest `DepthHazard` when present and updated within `DEPTH_HAZARD_TTL_MS`, `null` otherwise.
     */
    private fun getFreshDepthCandidate(nowMs: Long): DepthHazard? {
        val hazardAtMs = latestDepthHazardAtMs.get()
        if (hazardAtMs <= 0L) return null
        if (nowMs - hazardAtMs > DEPTH_HAZARD_TTL_MS) return null
        return latestDepthHazard.get()
    }

    /**
     * Determines whether a haptic pulse may be emitted based on the cooldown and updates the last-trigger timestamp when permitted.
     *
     * @param nowMs Current time in milliseconds.
     * @return `true` if the cooldown has elapsed and the haptic should be triggered (in which case `lastHapticAtMs` is updated to `nowMs`), `false` otherwise.
     */
    private fun shouldTriggerHaptic(nowMs: Long): Boolean {
        if (nowMs - lastHapticAtMs < HAPTIC_COOLDOWN_MS) return false
        lastHapticAtMs = nowMs
        return true
    }

    /**
     * Builds a concise multi-line debug summary of the current detection, depth, fusion, speech, and sensitivity state.
     *
     * @param yoloCandidate The selected YOLO detection candidate, or `null` if none.
     * @param yoloCompositeScore Composite score computed for the YOLO candidate, or `null`.
     * @param depthCandidate The most recent depth-based hazard, or `null` if none is fresh.
     * @param fusedAlert The fused hazard alert combining YOLO and depth inputs, or `null` if no alert.
     * @param speechSpoken `true` if the view model spoke the latest announcement, `false` otherwise.
     * @param speechSuppressedByHeadset `true` if speech was suppressed due to a connected headset.
     * @param sensitivity Current alert sensitivity value.
     * @return A multi-line string containing five lines: YOLO candidate summary, MiDaS depth hazard summary,
     * fusion result summary, speech state, and sensitivity configuration.
     */
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

    /**
     * Formats a floating-point number to a string with two decimal places.
     *
     * @param value The float value to format.
     * @return The formatted string with exactly two digits after the decimal point.
     */
    @SuppressLint("DefaultLocale")
    private fun fmt(value: Float): String = String.format("%.2f", value)

    /**
     * Determines whether an external audio headset or similar output device is currently connected.
     *
     * Considers wired headsets/headphones, USB headsets, and Bluetooth audio (A2DP/SCO) as connected devices.
     * On API 23+ this queries AudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) for those device types;
     * on older APIs it falls back to legacy audio manager flags.
     *
     * @return `true` if any of the considered headset/output device types are connected, `false` otherwise.
     */
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
