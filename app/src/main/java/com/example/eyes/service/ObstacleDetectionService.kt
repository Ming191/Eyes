package com.example.eyes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.example.eyes.R
import com.example.eyes.ai.AlertSource
import com.example.eyes.ai.DepthHazard
import com.example.eyes.ai.DepthHazardDetector
import com.example.eyes.ai.DepthMap
import com.example.eyes.ai.HazardFusionEngine
import com.example.eyes.ai.MiDasDepthEstimator
import com.example.eyes.ai.SpeechRateLimiter
import com.example.eyes.ai.YoloDetector
import com.example.eyes.ai.Zone
import com.example.eyes.camera.CameraManager
import com.example.eyes.camera.FrameThrottle
import com.example.eyes.camera.toBitmapWithRotation
import com.example.eyes.data.DataStoreManager
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ObstacleDetectionService : LifecycleService() {

    private val cameraManager: CameraManager by inject()
    private val yoloDetector: YoloDetector by inject()
    private val miDasDepthEstimator: MiDasDepthEstimator by inject()
    private val ttsService: TtsService by inject()
    private val hapticService: HapticService by inject()
    private val dataStoreManager: DataStoreManager by inject()
    private val audioManager: AudioManager by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val frameThrottle = FrameThrottle()
    private val depthHazardDetector = DepthHazardDetector()
    private val hazardFusionEngine = HazardFusionEngine()
    private val speechRateLimiter = SpeechRateLimiter(cooldownMs = SPEECH_COOLDOWN_MS)
    private val depthHazardAtMs = AtomicLong(0L)
    private var lastHapticAtMs: Long = 0L
    private val isFrameProcessing = AtomicBoolean(false)
    private val depthMap = AtomicReference<DepthMap?>(null)
    private val depthHazard = AtomicReference<DepthHazard?>(null)
    private val depthUpdating = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)

    @Volatile
    private var alertSensitivity: Float = DEFAULT_ALERT_SENSITIVITY

    override fun onCreate() {
        super.onCreate()
        running = true

        createNotificationChannel()
        serviceScope.launch {
            dataStoreManager.alertSensitivityFlow.collect { value ->
                alertSensitivity = value
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startAsForeground()
        startCameraAnalysis()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        cameraManager.unbindAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startCameraAnalysis() {
        cameraManager.bindAnalysisToLifecycle(this) { imageProxy ->
            if (!frameThrottle.shouldProcess(System.currentTimeMillis())) {
                imageProxy.close()
                return@bindAnalysisToLifecycle
            }

            if (!isFrameProcessing.compareAndSet(false, true)) {
                imageProxy.close()
                return@bindAnalysisToLifecycle
            }

            serviceScope.launch {
                try {
                    val bitmap = imageProxy.toBitmapWithRotation()
                    maybeUpdateDepth(bitmap)

                    val detections = yoloDetector.detect(bitmap)
                    val latestDepth = depthMap.get()
                    if (latestDepth != null) {
                        detections.forEach { detection ->
                            detection.midasDepth = miDasDepthEstimator.depthAt(latestDepth, detection.bbox)
                        }
                    }

                    val candidate = detections
                        .asSequence()
                        .filter { it.isPriority() }
                        .filter { it.isNearby(alertSensitivity) }
                        .maxByOrNull { detection ->
                            val depthScore =
                                if (detection.midasDepth > 0f) detection.midasDepth else detection.bboxDepthScore
                            (depthScore * 0.7f) + (detection.confidence * 0.3f)
                        }

                    val nowMs = System.currentTimeMillis()
                    val depthCandidate = getFreshDepthCandidate(nowMs)
                    val fusedAlert = hazardFusionEngine.fuse(candidate, depthCandidate)

                    if (fusedAlert != null) {
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
                            fusedAlert.primarySource == AlertSource.YOLO && candidate != null -> {
                                "Chú ý! ${candidate.labelVi} ở ${candidate.zone.labelVi}."
                            }
                            else -> fusedAlert.speechText ?: "Chú ý! Có vật cản gần ${fusedAlert.primaryZone.labelVi}."
                        }

                        if (!isHeadsetConnected() && speechRateLimiter.shouldSpeak(nowMs)) {
                            ttsService.speak(announcement, TtsService.Priority.URGENT)
                            speechRateLimiter.record(nowMs)
                        }
                    }
                } finally {
                    imageProxy.close()
                    isFrameProcessing.set(false)
                }
            }
        }
    }

    private fun maybeUpdateDepth(bitmap: Bitmap) {
        val currentFrame = frameCounter.incrementAndGet()
        if (currentFrame % DEPTH_FRAME_INTERVAL != 0) return
        if (!depthUpdating.compareAndSet(false, true)) return

        val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        serviceScope.launch {
            try {
                val map = miDasDepthEstimator.estimateDepth(snapshot)
                depthMap.set(map)
                val hazard = depthHazardDetector.detect(map)
                depthHazard.set(hazard)
                depthHazardAtMs.set(if (hazard != null) System.currentTimeMillis() else 0L)
            } finally {
                depthUpdating.set(false)
            }
        }
    }

    private fun getFreshDepthCandidate(nowMs: Long): DepthHazard? {
        val hazardAtMs = depthHazardAtMs.get()
        if (hazardAtMs <= 0L) return null
        if (nowMs - hazardAtMs > DEPTH_HAZARD_TTL_MS) return null
        return depthHazard.get()
    }

    private fun shouldTriggerHaptic(nowMs: Long): Boolean {
        if (nowMs - lastHapticAtMs < HAPTIC_COOLDOWN_MS) return false
        lastHapticAtMs = nowMs
        return true
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eyes đang hoạt động")
            .setContentText("Chế độ phát hiện vật cản đang bật")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Obstacle Detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Thông báo dịch vụ phát hiện vật cản"
        }
        manager.createNotificationChannel(channel)
    }

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

    companion object {
        private const val CHANNEL_ID = "obstacle_detection_channel"
        private const val NOTIFICATION_ID = 101
        private const val ACTION_START = "com.example.eyes.service.action.START"
        private const val ACTION_STOP = "com.example.eyes.service.action.STOP"
        private const val DEPTH_FRAME_INTERVAL = 1
        private const val DEPTH_HAZARD_TTL_MS = 2_800L
        private const val HAPTIC_COOLDOWN_MS = 300L
        private const val SPEECH_COOLDOWN_MS = 1_300L
        private const val DEFAULT_ALERT_SENSITIVITY = 0.5f

        @Volatile
        private var running: Boolean = false

        fun toggle(context: Context) {
            if (running) {
                stop(context)
            } else {
                start(context)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, ObstacleDetectionService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ObstacleDetectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
