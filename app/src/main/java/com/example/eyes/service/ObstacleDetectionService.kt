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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.example.eyes.R
import com.example.eyes.ai.DepthHazardDetector
import com.example.eyes.ai.DepthHazardSnapshot
import com.example.eyes.ai.DepthMap
import com.example.eyes.ai.HazardAlertPipeline
import com.example.eyes.ai.HazardFusionEngine
import com.example.eyes.ai.MiDasDepthEstimator
import com.example.eyes.ai.YoloDetector
import com.example.eyes.ai.Zone
import com.example.eyes.camera.CameraManager
import com.example.eyes.camera.FrameThrottle
import com.example.eyes.camera.toBitmapWithRotation
import com.example.eyes.data.DataStoreManager
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    private val frameThrottle = FrameThrottle(intervalMs = 200L)
    private val depthHazardDetector = DepthHazardDetector()
    private val isFrameProcessing = AtomicBoolean(false)
    private val depthMap = AtomicReference<DepthMap?>(null)
    private val depthHazardSnapshot = AtomicReference(DepthHazardSnapshot(hazard = null, atMs = 0L))
    private val depthUpdating = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)
    private val hazardAlertPipeline = HazardAlertPipeline(
        hazardFusionEngine = HazardFusionEngine(),
        latestDepthHazardSnapshot = { depthHazardSnapshot.get() },
        isHeadsetConnected = { isHeadsetConnected() },
        dispatchHaptic = ::dispatchObstacleHaptic,
        speakUrgent = { announcement -> ttsService.speak(announcement, TtsService.Priority.URGENT) }
    )

    @Volatile
    private var alertSensitivity: Float = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY

    /**
     * Initializes the service when it is created.
     *
     * Sets the internal running flag, ensures the notification channel exists, and starts
     * collecting alert sensitivity updates from the data store so the service keeps its
     * alertSensitivity value current.
     */
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

    /**
     * Handles incoming start commands for the service, honoring a stop action and otherwise
     * initializing foreground operation and camera analysis.
     *
     * If `intent?.action == ACTION_STOP` the service is stopped and the method returns
     * `START_NOT_STICKY`. For any other start request the service begins foreground mode
     * and binds camera analysis, returning `START_STICKY`.
     *
     * @param intent The start `Intent` provided by the system; may be `null`.
     * @return `START_NOT_STICKY` when a stop action was received, `START_STICKY` otherwise.
     */
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

    /**
     * Cleans up runtime resources when the service is destroyed.
     *
     * Sets the running flag to false, unbinds all camera attachments, cancels the service coroutine scope,
     * and delegates to the superclass destruction logic.
     */
    override fun onDestroy() {
        running = false
        cameraManager.unbindAll()
        serviceScope.cancel()
        yoloDetector.close()
        super.onDestroy()
    }

    /**
     * Binds camera frame analysis to the service lifecycle and processes each allowed frame to detect
     * obstacles, enrich detections with depth, fuse detection and depth hazards, and emit haptic and
     * speech alerts.
     *
     * The processing pipeline is rate-limited and guarded to avoid concurrent per-frame work. For each
     * processed frame it updates a cached depth map when applicable, runs YOLO detection, assigns
     * MiDaS depth to detections when available, selects an alert candidate, fuses it with any recent
     * depth-based hazard, and triggers haptics and TTS according to rate limits and headset state.
     *
     * The incoming ImageProxy is always closed and the per-frame processing guard is reset when work
     * completes.
     */
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
                var bitmap: Bitmap? = null
                try {
                    val frameBitmap = imageProxy.toBitmapWithRotation()
                    bitmap = frameBitmap
                    maybeUpdateDepth(frameBitmap)

                    val detections = yoloDetector.detect(frameBitmap)
                    val latestDepth = depthMap.get()
                    if (latestDepth != null) {
                        detections.forEach { detection ->
                            detection.midasDepth = miDasDepthEstimator.depthAt(latestDepth, detection.bbox)
                        }
                    }

                    hazardAlertPipeline.process(
                        detections = detections,
                        alertSensitivity = alertSensitivity
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Obstacle frame processing failed", e)
                } finally {
                    recycleBitmapIfNeeded(bitmap)
                    imageProxy.close()
                    isFrameProcessing.set(false)
                }
            }
        }
    }

    /**
     * Conditionally schedules a depth estimation from the provided camera frame and updates the cached depth and hazard state.
     *
     * This function increments an internal frame counter and, when the frame falls on the configured depth interval and no other depth update is in progress, captures a snapshot of `bitmap`, launches a background depth estimation, and updates `depthMap` and `depthHazardSnapshot` based on the result.
     *
     * @param bitmap The latest camera frame used to create a snapshot for depth estimation. */
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
                depthHazardSnapshot.set(
                    DepthHazardSnapshot(
                        hazard = hazard,
                        atMs = if (hazard != null) System.currentTimeMillis() else 0L
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Depth update failed", e)
            } finally {
                recycleBitmapIfNeeded(snapshot)
                depthUpdating.set(false)
            }
        }
    }

    private fun recycleBitmapIfNeeded(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
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
     * Promotes the service to a foreground service with a persistent notification.
     *
     * Uses the camera foreground service type when the platform supports it; otherwise starts
     * the foreground service without a specific type.
     */
    private fun startAsForeground() {
        val notification = buildNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    /**
     * Builds the persistent notification displayed while the obstacle detection service runs in foreground.
     *
     * @return A Notification configured for the foreground obstacle detection service using CHANNEL_ID.
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eyes đang hoạt động")
            .setContentText("Chế độ phát hiện vật cản đang bật")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Creates and registers the persistent notification channel used by the service.
     *
     * No operation is performed on Android versions older than Oreo (SDK < 26).
     */
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

    /**
     * Determines whether an audio headset or Bluetooth audio output is currently connected.
     *
     * @return `true` if a wired headset/headphones, USB headset, or Bluetooth A2DP/SCO device is connected; `false` otherwise.
     */
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
        private const val TAG = "ObstacleDetectionService"
        private const val CHANNEL_ID = "obstacle_detection_channel"
        private const val NOTIFICATION_ID = 101
        private const val ACTION_START = "com.example.eyes.service.action.START"
        private const val ACTION_STOP = "com.example.eyes.service.action.STOP"
        private const val DEPTH_FRAME_INTERVAL = 1

        @Volatile
        private var running: Boolean = false

        /**
         * Toggles the ObstacleDetectionService: starts it when stopped or stops it when running.
         *
         * If the service is running this requests it to stop; otherwise it starts the service as a
         * foreground service using the provided Context.
         */
        fun toggle(context: Context) {
            if (running) {
                stop(context)
            } else {
                start(context)
            }
        }

        /**
         * Starts the obstacle detection foreground service.
         *
         * Sends a start intent with `ACTION_START` to launch the service as a foreground service.
         *
         * @param context Context used to start the service; typically an application or activity context.
         */
        fun start(context: Context) {
            val intent = Intent(context, ObstacleDetectionService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        /**
         * Requests the ObstacleDetectionService to stop running.
         *
         * Sends an intent with the service's stop action so the service can handle the stop request.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ObstacleDetectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
