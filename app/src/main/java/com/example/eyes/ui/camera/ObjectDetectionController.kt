package com.example.eyes.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.eyes.application.objectdetection.DetectObjectsUseCase
import com.example.eyes.application.objectdetection.ObjectDetectionAnnouncementPolicy
import com.example.eyes.application.objectdetection.WarmUpObjectDetectionUseCase
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class ObjectDetectionController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val detectObjectsUseCase: DetectObjectsUseCase,
    private val warmUpObjectDetectionUseCase: WarmUpObjectDetectionUseCase,
    private val speechOutput: SpeechOutput,
    private val bitmapStore: CameraBitmapStore,
    private val imageConverter: com.example.eyes.infrastructure.camera.CameraImageConverter,
    private val localizedTextProvider: LocalizedTextProvider,
    private val cameraText: () -> CameraText,
    private val appLanguage: () -> AppLanguage
) {
    private val isProcessingObjectDetection = AtomicBoolean(false)
    private val lastObjectDetectionAtMs = AtomicLong(0L)
    private val announcementPolicy = ObjectDetectionAnnouncementPolicy()

    fun warmUp(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            try {
                val outputs = warmUpObjectDetectionUseCase()
                if (uiState.value.activeMode == CameraMode.OBJECT_DETECTION) {
                    val message = cameraText().objectDetectionWarmupDone
                    Log.i(TAG, "Object detection warmup done: $outputs")
                    uiState.update {
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
                if (uiState.value.activeMode == CameraMode.OBJECT_DETECTION) {
                    val message = cameraText().objectDetectionWarmupFailed
                    Log.e(TAG, "Object detection warmup failed", error)
                    uiState.update {
                        it.copy(
                            statusMessage = message,
                            debugMetrics = "YOLO: ${error.message ?: error::class.java.simpleName}"
                        )
                    }
                }
            }
        }
    }

    fun processImageProxy(imageProxy: ImageProxy, scope: CoroutineScope) {
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

        scope.launch(Dispatchers.Default) {
            var bitmap: Bitmap? = null
            try {
                bitmap = imageConverter.toBitmapWithRotation(imageProxy)
                bitmapStore.replaceLatestFrame(bitmap)
                processObjectDetection(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Object detection frame failed", e)
            } finally {
                bitmapStore.recycle(bitmap)
                imageProxy.close()
                isProcessingObjectDetection.set(false)
            }
        }
    }

    suspend fun processObjectDetection(bitmap: Bitmap) {
        try {
            val detections = detectObjectsUseCase(imageConverter.toImageFrame(bitmap))
            if (uiState.value.activeMode != CameraMode.OBJECT_DETECTION) return

            val language = appLanguage()
            val overlayItems = detections.map { detection ->
                detection.toOverlayItem(bitmap.width, bitmap.height, localizedTextProvider, language)
            }
            Log.i(TAG, "Object detection frame: ${overlayItems.size} detections")
            val announcement = if (overlayItems.isNotEmpty()) {
                overlayItems.joinToString(separator = ". ") { item ->
                    cameraText().objectDetectionAnnouncement(item.label, item.positionText)
                }
            } else {
                cameraText().objectDetectionNoObjects
            }
            if (uiState.value.activeMode != CameraMode.OBJECT_DETECTION) return
            maybeSpeakObjectDetection(announcement, overlayItems.isNotEmpty())
            uiState.update { state ->
                if (state.activeMode != CameraMode.OBJECT_DETECTION) {
                    state
                } else {
                    state.copy(
                        objectDetections = overlayItems,
                        lastAnnouncement = announcement,
                        debugMetrics = cameraText().objectDetectionDebug(overlayItems.size)
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Object detection failed", error)
            uiState.update { state ->
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

    fun resetAnnouncementDebounce() {
        announcementPolicy.reset()
    }

    private fun maybeSpeakObjectDetection(
        announcement: String,
        hasObjects: Boolean
    ) {
        if (!announcementPolicy.shouldSpeak(announcement, hasObjects)) return
        Log.i(TAG, "Object detection TTS: $announcement")
        speechOutput.speak(
            text = announcement,
            locale = appLanguage().ttsLocale
        )
    }

    private companion object {
        private const val TAG = "CameraViewModel"
        private const val OBJECT_DETECTION_INTERVAL_MS = 1_000L
    }
}
