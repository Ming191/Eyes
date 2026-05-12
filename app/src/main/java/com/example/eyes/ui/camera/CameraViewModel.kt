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
import com.example.eyes.ai.DepthMap
import com.example.eyes.ai.DepthHazardDetector
import com.example.eyes.ai.DepthHazardSnapshot
import com.example.eyes.ai.Detection
import com.example.eyes.ai.HazardAlertPipeline
import com.example.eyes.ai.HazardFusionEngine
import com.example.eyes.ai.MiDasDepthEstimator
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
import java.util.concurrent.atomic.AtomicReference
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException

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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
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
                latestDepthHazardSnapshot.set(DepthHazardSnapshot(hazard = null, atMs = 0L))
                hazardAlertPipeline.resetSafeStatus()
                updateUiStateAndRecycleReplacedDepthPreview {
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
            try {
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
                        lastAnnouncement = description
                    )
                }

            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Mô tả cảnh thất bại, vui lòng thử lại"
                    )
                }
                hapticService.error()

            } finally {
                _uiState.update {
                    it.copy(isDescribingScene = false)
                }
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

    override fun onCleared() {
        yoloDetector.close()
        super.onCleared()
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
        updateUiStateAndRecycleReplacedDepthPreview {
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
     * - Updates `latestDepthMap` and `latestDepthHazardSnapshot`.
     * - Updates the UI state's `depthPreviewBitmap`.
     */
    private fun maybeRefreshDepth(bitmap: Bitmap) {
        val currentFrameIndex = frameCounter.incrementAndGet()
        if (currentFrameIndex % DEPTH_FRAME_INTERVAL != 0) return
        if (!isDepthUpdating.compareAndSet(false, true)) return

        val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val newMap = try {
                    miDasDepthEstimator.estimateDepth(snapshot)
                } finally {
                    recycleBitmapIfNeeded(snapshot)
                }
                latestDepthMap.set(newMap)
                val hazard = depthHazardDetector.detect(newMap)
                latestDepthHazardSnapshot.set(
                    DepthHazardSnapshot(
                        hazard = hazard,
                        atMs = if (hazard != null) System.currentTimeMillis() else 0L
                    )
                )
                val previewBitmap = buildDepthPreviewBitmap(newMap)
                updateUiStateAndRecycleReplacedDepthPreview { state ->
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
        if (previousPreview !== nextPreview) {
            recycleBitmapIfNeeded(previousPreview)
        }
    }

    private fun recycleBitmapIfNeeded(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    /**
     * Runs the shared hazard alert pipeline and applies its result to camera UI state.
     *
     * @param detections The list of detections from the current frame to evaluate as alert candidates.
     */
    private fun handleObstacleAlert(detections: List<Detection>) {
        val result = hazardAlertPipeline.process(
            detections = detections,
            alertSensitivity = alertSensitivity.value
        )
        _uiState.update { state ->
            state.copy(
                statusMessage = result.statusMessage ?: state.statusMessage,
                lastAnnouncement = result.lastAnnouncement ?: state.lastAnnouncement,
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
        private const val MAX_OVERLAY_BOXES = 8
    }
}
