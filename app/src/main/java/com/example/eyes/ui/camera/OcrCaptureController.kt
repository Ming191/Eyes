package com.example.eyes.ui.camera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.eyes.application.navigation.SetCameraOcrModeUseCase
import com.example.eyes.application.ocr.OcrFallbackReason
import com.example.eyes.application.ocr.RecognizeOcrDocumentInput
import com.example.eyes.application.ocr.RecognizeOcrDocumentUseCase
import com.example.eyes.application.ports.HapticFeedback
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.ocr.OcrResult
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.infrastructure.camera.CameraImageConverter
import com.example.eyes.infrastructure.ocr.OcrExperimentLogEntry
import com.example.eyes.infrastructure.ocr.OcrExperimentLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class OcrCaptureController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val currentOcrMode: StateFlow<OcrMode>,
    private val recognizeOcrDocumentUseCase: RecognizeOcrDocumentUseCase,
    private val setCameraOcrModeUseCase: SetCameraOcrModeUseCase,
    private val ocrDocumentController: OcrDocumentController,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticFeedback,
    private val imageConverter: CameraImageConverter,
    private val ocrExperimentLogger: OcrExperimentLogger,
    private val cameraText: () -> CameraText,
    private val appLanguage: () -> AppLanguage,
    private val resetGuidance: () -> Unit,
    private val updateUiStateAndRecycleReplacedBitmap: ((CameraUiState) -> CameraUiState) -> Unit
) {
    private val lastRawOcrResult = AtomicReference<OcrResult?>(null)
    private val lastOcrUsedFallback = AtomicBoolean(false)

    fun processCapturedImage(imageProxy: ImageProxy, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            resetGuidance()
            speechOutput.stop()
            val bitmap = try {
                imageConverter.toBitmapWithRotation(imageProxy)
            } catch (_: Throwable) {
                uiState.update { it.copy(isOcrScanning = false, statusMessage = cameraText().cannotProcessCapturedImage) }
                hapticService.error()
                return@launch
            } finally {
                imageProxy.close()
            }

            updateUiStateAndRecycleReplacedBitmap {
                it.resetOcrRuntime(cameraText(), guidanceMessage = cameraText().processingOcrImage).copy(
                    isOcrScanning = true,
                    ocrCapturedBitmap = bitmap,
                    statusMessage = cameraText().processingOcrImage
                )
            }

            val requestedMode = currentOcrMode.value
            val processingStartedAtMs = SystemClock.elapsedRealtime()
            val recognizedDocument = runCatching {
                recognizeOcrDocumentUseCase(
                    RecognizeOcrDocumentInput(
                        imageFrame = imageConverter.toImageFrame(bitmap),
                        mode = requestedMode,
                        translateToVietnamese = uiState.value.ocrTranslateToVietnamese
                    )
                )
            }.getOrElse { error ->
                logOcrExperimentResult(
                    OcrExperimentLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        mode = requestedMode,
                        processingTimeMs = SystemClock.elapsedRealtime() - processingStartedAtMs,
                        recognizedText = "",
                        usedFallback = false,
                        fallbackReason = null,
                        translationFailed = false,
                        error = error.message ?: error::class.simpleName
                    )
                )
                uiState.update {
                    it.copy(isOcrScanning = false, statusMessage = cameraText().ocrFailed(error.message ?: cameraText().unknownReason))
                }
                hapticService.error()
                return@launch
            }
            logOcrExperimentResult(
                OcrExperimentLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    mode = requestedMode,
                    processingTimeMs = SystemClock.elapsedRealtime() - processingStartedAtMs,
                    recognizedText = recognizedDocument.rawResult.fullText,
                    usedFallback = recognizedDocument.usedFallbackFromAccuracy,
                    fallbackReason = recognizedDocument.fallbackReason.toLogValue(),
                    translationFailed = recognizedDocument.translationFailed,
                    error = null
                )
            )
            lastRawOcrResult.set(recognizedDocument.rawResult)
            lastOcrUsedFallback.set(recognizedDocument.usedFallbackFromAccuracy)
            if (recognizedDocument.translationFailed) {
                speechOutput.speakAndAwait(
                    cameraText().cannotTranslateReadingOriginal,
                    appLanguage().ttsLocale
                )
            }
            if (recognizedDocument.usedFallbackFromAccuracy) {
                setCameraOcrModeUseCase(OcrMode.QUICK)
                val reason = recognizedDocument.fallbackReason.toLocalizedReason()
                speechOutput.speakAndAwait(
                    cameraText().accuracyOcrFallback(reason),
                    appLanguage().ttsLocale
                )
            }
            ocrDocumentController.enterOcrDocumentMode(
                result = recognizedDocument.resultForSpeech,
                usedFallback = recognizedDocument.usedFallbackFromAccuracy,
                canTranslateCurrentDocument = recognizedDocument.canTranslateDocument
            )
        }
    }

    fun onCaptureError() {
        uiState.update { it.copy(isOcrScanning = false, statusMessage = cameraText().cannotCaptureOcrTryAgain) }
        hapticService.error()
    }

    fun onCaptureRequested() {
        speechOutput.stop()
        if (!uiState.value.isOcrReadyToCapture) {
            speechOutput.speak(cameraText().imageMayBeUnclearCapturing, appLanguage().ttsLocale)
        }
    }

    fun prepareForNextCapture() {
        clearLastResult()
        resetGuidance()
        uiState.update {
            it.resetOcrRuntime(cameraText()).copy(
                statusMessage = cameraText().readyToCaptureOcr,
                lastAnnouncement = cameraText().readyForNewCapture
            )
        }
        speechOutput.stop()
    }

    fun clearLastResult() {
        lastRawOcrResult.set(null)
        lastOcrUsedFallback.set(false)
    }

    private suspend fun logOcrExperimentResult(entry: OcrExperimentLogEntry) {
        runCatching {
            ocrExperimentLogger.append(entry)
        }.onFailure { error ->
            Log.e(TAG, "Failed to write OCR experiment result", error)
        }
    }

    private fun OcrFallbackReason?.toLogValue(): String? = when (this) {
        OcrFallbackReason.GptRefused -> "gpt_refused"
        OcrFallbackReason.ApiKey -> "api_key"
        OcrFallbackReason.ModelPermission -> "model_permission"
        OcrFallbackReason.Quota -> "quota"
        OcrFallbackReason.Timeout -> "timeout"
        is OcrFallbackReason.EngineError -> message ?: "engine_error"
        OcrFallbackReason.Unknown -> "unknown"
        null -> null
    }

    private fun OcrFallbackReason?.toLocalizedReason(): String = when (this) {
        OcrFallbackReason.GptRefused -> cameraText().gptRefusedReason
        OcrFallbackReason.ApiKey -> cameraText().apiKeyReason
        OcrFallbackReason.ModelPermission -> cameraText().modelPermissionReason
        OcrFallbackReason.Quota -> cameraText().quotaReason
        OcrFallbackReason.Timeout -> cameraText().timeoutReason
        is OcrFallbackReason.EngineError -> message ?: cameraText().unknownError
        OcrFallbackReason.Unknown,
        null -> cameraText().unknownReason
    }

    private companion object {
        const val TAG = "OcrCaptureController"
    }
}
