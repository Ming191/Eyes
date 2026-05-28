package com.example.eyes.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.eyes.application.ports.CurrencyRecognizerFactory
import com.example.eyes.application.ports.CurrencyRecognizerPort
import com.example.eyes.domain.haptics.HapticFeedback
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.speech.SpeechOutput
import com.example.eyes.infrastructure.camera.toBitmapWithRotation
import com.example.eyes.infrastructure.camera.toImageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class CurrencyRecognitionController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val currencyRecognizerFactory: CurrencyRecognizerFactory,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticFeedback,
    private val bitmapStore: CameraBitmapStore,
    private val currencyTextMapper: CurrencyTextMapper,
    private val cameraText: () -> CameraText,
    private val appLanguage: () -> AppLanguage,
    private val updateUiStateAndRecycleReplacedBitmap: ((CameraUiState) -> CameraUiState) -> Unit
) {
    private val isProcessingCurrencyPreview = AtomicBoolean(false)
    private val lastCurrencyPreviewAtMs = AtomicLong(0L)
    private val lastCurrencyNoDetectionAtMs = AtomicLong(0L)
    private val lastCurrencyAnnouncement = AtomicReference("")
    private var currencyAnalyzer: CurrencyRecognizerPort? = null

    fun processPreviewImageProxy(imageProxy: ImageProxy, scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        if (now - lastCurrencyPreviewAtMs.get() < CURRENCY_PREVIEW_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        if (!isProcessingCurrencyPreview.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastCurrencyPreviewAtMs.set(now)

        scope.launch(Dispatchers.Default) {
            var bitmap: Bitmap? = null
            try {
                val analyzer = getCurrencyAnalyzer() ?: return@launch
                bitmap = imageProxy.toBitmapWithRotation()
                analyzer.analyze(bitmap.toImageFrame())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Currency preview frame failed", error)
            } finally {
                bitmapStore.recycle(bitmap)
                imageProxy.close()
                isProcessingCurrencyPreview.set(false)
            }
        }
    }

    fun onCaptureRequested() {
        val state = uiState.value
        if (state.isCurrencyScanning || state.ocrCapturedBitmap != null) return
        uiState.update {
            it.copy(
                isCurrencyScanning = true,
                statusMessage = cameraText().processingCurrencyImage,
                lastAnnouncement = cameraText().processingCurrencyImage
            )
        }
        hapticService.loading()
        speechOutput.speak(
            cameraText().processingCurrencyImage,
            appLanguage().ttsLocale
        )
    }

    fun processCapturedImage(imageProxy: ImageProxy, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            val capturedBitmap = try {
                imageProxy.toBitmapWithRotation()
            } catch (_: Throwable) {
                uiState.update {
                    it.copy(
                        isCurrencyScanning = false,
                        statusMessage = cameraText().cannotCaptureCurrencyTryAgain,
                        lastAnnouncement = cameraText().cannotCaptureCurrencyTryAgain
                    )
                }
                hapticService.error()
                return@launch
            } finally {
                imageProxy.close()
            }

            updateUiStateAndRecycleReplacedBitmap {
                it.copy(
                    ocrCapturedBitmap = capturedBitmap,
                    isCurrencyScanning = true,
                    currencyDisplay = "",
                    currencyConfidence = 0f,
                    statusMessage = cameraText().processingCurrencyImage,
                    lastAnnouncement = cameraText().processingCurrencyImage
                )
            }

            val analyzer = getCurrencyAnalyzer()
            if (analyzer == null) {
                uiState.update {
                    it.copy(
                        isCurrencyScanning = false,
                        statusMessage = cameraText().currencyModelInitError,
                        lastAnnouncement = cameraText().currencyModelInitError
                    )
                }
                hapticService.error()
                return@launch
            }

            runCatching {
                analyzer.resetBuffer()
                analyzer.analyze(capturedBitmap.toImageFrame())
            }.onFailure { error ->
                Log.e(TAG, "Currency capture failed", error)
                uiState.update {
                    it.copy(
                        isCurrencyScanning = false,
                        statusMessage = cameraText().cannotCaptureCurrencyTryAgain,
                        lastAnnouncement = cameraText().cannotCaptureCurrencyTryAgain
                    )
                }
                hapticService.error()
            }
        }
    }

    fun onCaptureError() {
        uiState.update {
            it.copy(
                isCurrencyScanning = false,
                statusMessage = cameraText().cannotCaptureCurrencyTryAgain,
                lastAnnouncement = cameraText().cannotCaptureCurrencyTryAgain
            )
        }
        hapticService.error()
    }

    fun prepareForNextCapture() {
        resetAnnouncementDebounce()
        currencyAnalyzer?.resetBuffer()
        updateUiStateAndRecycleReplacedBitmap {
            it.copy(
                ocrCapturedBitmap = null,
                isCurrencyScanning = false,
                currencyDisplay = "",
                currencyConfidence = 0f,
                statusMessage = cameraText().waitingForClearMoneyImage,
                lastAnnouncement = cameraText().waitingForClearMoneyImage
            )
        }
        speechOutput.stop()
    }

    fun resetBuffer() {
        currencyAnalyzer?.resetBuffer()
    }

    fun resetAnnouncementDebounce() {
        lastCurrencyAnnouncement.set("")
        lastCurrencyNoDetectionAtMs.set(0L)
    }

    fun close() {
        currencyAnalyzer?.close()
    }

    private fun getCurrencyAnalyzer(): CurrencyRecognizerPort? {
        currencyAnalyzer?.let { return it }
        return try {
            currencyRecognizerFactory.create(::onCurrencyResult).also { analyzer ->
                currencyAnalyzer = analyzer
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Currency model load failed", error)
            val message = cameraText().currencyModelLoadError
            uiState.update {
                it.copy(
                    statusMessage = message,
                    lastAnnouncement = message,
                    currencyDisplay = "",
                    currencyConfidence = 0f
                )
            }
            null
        }
    }

    private fun onCurrencyResult(label: String, confidence: Float) {
        if (uiState.value.activeMode != CameraMode.CURRENCY) return
        val safeConfidence = confidence.coerceIn(0f, 1f)

        if (label == CurrencyRecognizerPort.EMPTY_LABEL) {
            val hadResult = uiState.value.currencyDisplay.isNotEmpty()
            if (hadResult) {
                resetAnnouncementDebounce()
                currencyAnalyzer?.resetBuffer()
            }
            maybeSpeakNoCurrencyDetected()
            uiState.update { state ->
                if (state.activeMode != CameraMode.CURRENCY) {
                    state
                } else {
                    state.copy(
                        currencyDisplay = "",
                        currencyConfidence = 0f,
                        isCurrencyScanning = false,
                        statusMessage = cameraText().noCurrencyDetected,
                        lastAnnouncement = cameraText().noCurrencyDetected
                    )
                }
            }
            return
        }

        val display = currencyTextMapper.display(label)
        val spoken = currencyTextMapper.spoken(label)
        val confidencePercent = String.format(Locale.getDefault(), "%.0f%%", safeConfidence * 100f)

        if (lastCurrencyAnnouncement.get() != label) {
            lastCurrencyAnnouncement.set(label)
            hapticService.confirm()
            speechOutput.speak(spoken, appLanguage().ttsLocale)
        }

        uiState.update { state ->
            if (state.activeMode != CameraMode.CURRENCY) {
                state
            } else {
                state.copy(
                    currencyDisplay = display,
                    currencyConfidence = safeConfidence,
                    isCurrencyScanning = false,
                    statusMessage = cameraText().currencyDetectedStatus(display, confidencePercent),
                    lastAnnouncement = spoken,
                    debugMetrics = cameraText().currencyDebug(confidencePercent)
                )
            }
        }
    }

    private fun maybeSpeakNoCurrencyDetected() {
        val now = System.currentTimeMillis()
        if (now - lastCurrencyNoDetectionAtMs.get() < CURRENCY_NO_DETECTION_REPEAT_MS) return

        lastCurrencyNoDetectionAtMs.set(now)
        speechOutput.speak(
            cameraText().noCurrencyDetected,
            appLanguage().ttsLocale
        )
    }

    private companion object {
        private const val TAG = "CurrencyRecognitionController"
        private const val CURRENCY_PREVIEW_INTERVAL_MS = 1_500L
        private const val CURRENCY_NO_DETECTION_REPEAT_MS = 10_000L
    }
}
