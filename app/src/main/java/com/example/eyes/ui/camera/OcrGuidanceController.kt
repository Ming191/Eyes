package com.example.eyes.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.eyes.application.ports.OcrGuidanceAnalyzerPort
import com.example.eyes.application.ports.HapticFeedback
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrGuidanceEvaluator
import com.example.eyes.domain.ocr.OcrGuidanceStatus
import com.example.eyes.domain.ocr.OcrGuidanceTracker
import com.example.eyes.application.ports.SpeechOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class OcrGuidanceController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val analyzer: OcrGuidanceAnalyzerPort,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticFeedback,
    private val bitmapStore: CameraBitmapStore,
    private val imageConverter: com.example.eyes.infrastructure.camera.CameraImageConverter,
    private val cameraText: () -> CameraText,
    private val appLanguage: () -> AppLanguage
) {
    private val isProcessing = AtomicBoolean(false)
    private val lastGuidanceAtMs = AtomicLong(0L)
    private val tracker = OcrGuidanceTracker()

    fun processImageProxy(imageProxy: ImageProxy, scope: CoroutineScope) {
        val state = uiState.value
        if (state.isOcrScanning || state.isOcrDocumentMode || state.ocrCapturedBitmap != null) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastGuidanceAtMs.get() < OCR_GUIDANCE_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastGuidanceAtMs.set(now)

        scope.launch(Dispatchers.Default) {
            var bitmap: Bitmap? = null
            try {
                bitmap = imageConverter.toBitmapWithRotation(imageProxy)
                bitmapStore.replaceLatestFrame(bitmap)
                val frame = analyzer.analyze(imageConverter.toImageFrame(bitmap))
                val stableCount = tracker.updateStability(frame.textBounds)
                val evaluation = OcrGuidanceEvaluator.evaluate(
                    frame = frame,
                    stableFrameCount = stableCount,
                    text = cameraText().ocrGuidanceText()
                )
                val currentState = uiState.value
                if (
                    currentState.activeMode != CameraMode.OCR ||
                    currentState.isOcrScanning ||
                    currentState.isOcrDocumentMode ||
                    currentState.ocrCapturedBitmap != null
                ) {
                    return@launch
                }
                uiState.update {
                    it.copy(
                        ocrGuidanceStatus = evaluation.status,
                        ocrGuidanceMessage = evaluation.message,
                        isOcrReadyToCapture = evaluation.isReadyToCapture,
                        ocrGuidanceBounds = evaluation.textBounds,
                        statusMessage = evaluation.message,
                        lastAnnouncement = evaluation.message
                    )
                }
                announceIfNeeded(evaluation.status, evaluation.message)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "OCR guidance failed", error)
            } finally {
                bitmapStore.recycle(bitmap)
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }

    fun reset() {
        tracker.reset()
        lastGuidanceAtMs.set(0L)
    }

    fun close() {
        analyzer.close()
    }

    private fun announceIfNeeded(status: OcrGuidanceStatus, message: String) {
        if (!tracker.shouldAnnounce(status)) return
        hapticService.confirm()
        speechOutput.speak(message, appLanguage().ttsLocale)
    }

    private companion object {
        private const val TAG = "OcrGuidanceController"
        private const val OCR_GUIDANCE_INTERVAL_MS = 700L
    }
}
