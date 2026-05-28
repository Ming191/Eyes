package com.example.eyes.ui.camera

import android.graphics.Bitmap
import android.util.Log
import com.example.eyes.application.scene.DescribeSceneUseCase
import com.example.eyes.domain.audio.AudioRouteProvider
import com.example.eyes.domain.haptics.HapticFeedback
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionError
import com.example.eyes.domain.speech.SpeechOutput
import com.example.eyes.infrastructure.camera.toImageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class SceneCaptureController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val describeSceneUseCase: DescribeSceneUseCase,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticFeedback,
    private val audioRouteProvider: AudioRouteProvider,
    private val cameraText: () -> CameraText,
    private val appLanguage: () -> AppLanguage,
    private val updateUiStateAndRecycleReplacedBitmap: ((CameraUiState) -> CameraUiState) -> Unit
) {
    fun onSceneCaptureRequested() {
        if (uiState.value.isDescribingScene || uiState.value.ocrCapturedBitmap != null) return
        uiState.update {
            it.copy(
                isDescribingScene = true,
                statusMessage = cameraText().describingScenePleaseWait,
                lastAnnouncement = cameraText().describingScenePleaseWait
            )
        }
        hapticService.loading()
    }

    fun onSceneBitmapCaptured(capturedBitmap: Bitmap) {
        updateUiStateAndRecycleReplacedBitmap {
            it.copy(
                ocrCapturedBitmap = capturedBitmap,
                isDescribingScene = true,
                statusMessage = cameraText().describingScenePleaseWait,
                lastAnnouncement = cameraText().describingScenePleaseWait
            )
        }
    }

    fun onSceneCaptureError() {
        uiState.update {
            it.copy(
                isDescribingScene = false,
                statusMessage = cameraText().cannotCaptureSceneTryAgain,
                lastAnnouncement = cameraText().cannotCaptureSceneTryAgain
            )
        }
        hapticService.error()
    }

    fun prepareForNextSceneCapture() {
        updateUiStateAndRecycleReplacedBitmap {
            it.copy(
                ocrCapturedBitmap = null,
                isDescribingScene = false,
                statusMessage = cameraText().waitingForSceneFrame,
                lastAnnouncement = cameraText().waitingForSceneFrame
            )
        }
        speechOutput.stop()
    }

    fun finishSceneDescription() {
        uiState.update { it.copy(isDescribingScene = false) }
    }

    suspend fun describeCapturedScene(capturedBitmap: Bitmap) {
        try {
            val language = appLanguage()
            when (val result = describeSceneUseCase(imageFrame = capturedBitmap.toImageFrame(), language = language)) {
                is SceneDescription.Success -> {
                    if (!audioRouteProvider.isHeadsetConnected()) {
                        speechOutput.speak(result.text, language.ttsLocale)
                    }
                    hapticService.confirm()
                    uiState.update {
                        it.copy(
                            statusMessage = cameraText().sceneDescriptionDone,
                            lastAnnouncement = result.text
                        )
                    }
                }

                is SceneDescription.Failure -> {
                    if (result.error == SceneDescriptionError.OFFLINE) {
                        val fallback = cameraText().sceneDescriptionOfflineFallback
                        if (!audioRouteProvider.isHeadsetConnected()) {
                            speechOutput.speak(fallback, language.ttsLocale)
                        }
                        hapticService.confirm()
                        uiState.update {
                            it.copy(
                                statusMessage = cameraText().sceneDescriptionDone,
                                lastAnnouncement = fallback
                            )
                        }
                        return
                    }
                    val userMessage = cameraText().sceneDescriptionError(result.error)
                    if (!audioRouteProvider.isHeadsetConnected()) {
                        speechOutput.speak(userMessage, language.ttsLocale)
                    }
                    hapticService.error()
                    uiState.update {
                        it.copy(
                            statusMessage = cameraText().sceneDescriptionFailed,
                            lastAnnouncement = userMessage
                        )
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Describe captured scene failed", error)
            uiState.update {
                it.copy(
                    statusMessage = cameraText().sceneDescriptionFailed,
                    lastAnnouncement = cameraText().sceneDescriptionFailed
                )
            }
            hapticService.error()
        }
    }

    private companion object {
        private const val TAG = "SceneCaptureController"
    }
}
