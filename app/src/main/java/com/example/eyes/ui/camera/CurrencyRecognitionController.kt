package com.example.eyes.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.eyes.application.currency.CurrencyRecognitionPolicy
import com.example.eyes.application.currency.RecognizeCurrencyUseCase
import com.example.eyes.application.ports.HapticFeedback
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.application.ports.SpeechOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class CurrencyRecognitionController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val recognizeCurrencyUseCase: RecognizeCurrencyUseCase,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticFeedback,
    private val bitmapStore: CameraBitmapStore,
    private val imageConverter: com.example.eyes.infrastructure.camera.CameraImageConverter,
    private val currencyTextMapper: CurrencyTextMapper,
    private val cameraText: () -> CameraText,
    private val appLanguage: () -> AppLanguage,
    private val updateUiStateAndRecycleReplacedBitmap: ((CameraUiState) -> CameraUiState) -> Unit
) {
    private val isProcessingCurrencyPreview = AtomicBoolean(false)
    private val lastCurrencyPreviewAtMs = AtomicLong(0L)
    private val recognitionPolicy = CurrencyRecognitionPolicy()
    private val onCurrencyResultCallback: (String, Float) -> Unit = ::onCurrencyResult

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
                if (!ensureCurrencyRecognizer()) return@launch
                bitmap = imageConverter.toBitmapWithRotation(imageProxy)
                recognizeCurrencyUseCase.analyze(imageConverter.toImageFrame(bitmap), onCurrencyResultCallback)
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
                imageConverter.toBitmapWithRotation(imageProxy)
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

            if (!ensureCurrencyRecognizer()) {
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
                recognizeCurrencyUseCase.resetBuffer()
                recognizeCurrencyUseCase.analyze(imageConverter.toImageFrame(capturedBitmap), onCurrencyResultCallback)
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
        recognizeCurrencyUseCase.resetBuffer()
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
        recognizeCurrencyUseCase.resetBuffer()
    }

    fun resetAnnouncementDebounce() {
        recognitionPolicy.resetAnnouncementDebounce()
    }

    fun close() {
        recognizeCurrencyUseCase.close()
    }

    private fun ensureCurrencyRecognizer(): Boolean {
        return try {
            recognizeCurrencyUseCase.prepare(onCurrencyResultCallback)
            true
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
            false
        }
    }

    private fun onCurrencyResult(label: String, confidence: Float) {
        if (uiState.value.activeMode != CameraMode.CURRENCY) return
        val decision = recognitionPolicy.onResult(
            label = label,
            confidence = confidence,
            hadDisplay = uiState.value.currencyDisplay.isNotEmpty()
        )

        if (decision.isEmpty) {
            if (decision.shouldResetBuffer) {
                recognizeCurrencyUseCase.resetBuffer()
            }
            if (decision.shouldSpeakNoDetection) {
                speechOutput.speak(
                    cameraText().noCurrencyDetected,
                    appLanguage().ttsLocale
                )
            }
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

        val detectedLabel = decision.label ?: return
        val display = currencyTextMapper.display(detectedLabel)
        val spoken = currencyTextMapper.spoken(detectedLabel)
        val confidencePercent = String.format(Locale.getDefault(), "%.0f%%", decision.safeConfidence * 100f)

        if (decision.shouldSpeakDetected) {
            hapticService.confirm()
            speechOutput.speak(spoken, appLanguage().ttsLocale)
        }

        uiState.update { state ->
            if (state.activeMode != CameraMode.CURRENCY) {
                state
            } else {
                state.copy(
                    currencyDisplay = display,
                    currencyConfidence = decision.safeConfidence,
                    isCurrencyScanning = false,
                    statusMessage = cameraText().currencyDetectedStatus(display, confidencePercent),
                    lastAnnouncement = spoken,
                    debugMetrics = cameraText().currencyDebug(confidencePercent)
                )
            }
        }
    }

    private companion object {
        private const val TAG = "CurrencyRecognitionController"
        private const val CURRENCY_PREVIEW_INTERVAL_MS = 1_500L
    }
}
