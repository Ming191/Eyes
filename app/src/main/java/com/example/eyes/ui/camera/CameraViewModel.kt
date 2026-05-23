package com.example.eyes.ui.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyes.R
import com.example.eyes.camera.CurrencyAnalyzer
import com.example.eyes.system.HapticService
import com.example.eyes.system.SpeechOutput
import com.example.eyes.ui.navigation.CameraMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Immutable
data class CameraUiState(
    val title: String = "",
    val summary: String = "",
    val statusMessage: String = "",
    val currentMode: CameraMode = CameraMode.Navigation,
    val currencyDisplay: String = "",
    val currencyConfidence: Float = 0f,
)

class CameraViewModel(
    private val tts: SpeechOutput,
    private val haptic: HapticService,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CameraUiState(
            title = context.getString(R.string.camera_ready_title),
            summary = context.getString(R.string.camera_navigation_summary),
            statusMessage = context.getString(R.string.camera_waiting_for_frame),
        )
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private var lastSpokenLabel = ""
    private var currencyAnalyzer: CurrencyAnalyzer? = null

    private fun getCurrencyAnalyzer(): CurrencyAnalyzer? {
        currencyAnalyzer?.let { return it }
        return try {
            CurrencyAnalyzer(context) { label, confidence ->
                onCurrencyResult(label, confidence)
            }.also { currencyAnalyzer = it }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot load currency model", e)
            _uiState.update { it.copy(statusMessage = context.getString(R.string.currency_model_load_error)) }
            null
        }
    }

    private fun onCurrencyResult(label: String, confidence: Float) {
        if (_uiState.value.currentMode != CameraMode.Currency) return
        val safeConfidence = confidence.coerceIn(0f, 1f)

        if (label == CurrencyAnalyzer.EMPTY_LABEL) {
            val hadResult = _uiState.value.currencyDisplay.isNotEmpty()
            if (hadResult) {
                lastSpokenLabel = ""
                currencyAnalyzer?.resetBuffer()
            }
            _uiState.update {
                it.copy(
                    currencyDisplay = "",
                    currencyConfidence = 0f,
                    statusMessage = context.getString(R.string.currency_instruction),
                )
            }
            return
        }

        val display = currencyDisplay(label)
        val spokenLabel = currencySpokenLabel(label)

        if (label != lastSpokenLabel) {
            lastSpokenLabel = label
            tts.speak(spokenLabel)
            haptic.confirm()
        }

        _uiState.update {
            it.copy(
                currencyDisplay = display,
                currencyConfidence = safeConfidence,
                statusMessage = context.getString(
                    R.string.currency_detected_status,
                    display,
                    String.format(Locale.getDefault(), "%.0f%%", safeConfidence * 100),
                ),
            )
        }
    }

    fun setMode(mode: CameraMode) {
        if (_uiState.value.currentMode == CameraMode.Currency && mode != CameraMode.Currency) {
            lastSpokenLabel = ""
            currencyAnalyzer?.resetBuffer()
        }

        _uiState.update { state ->
            when (mode) {
                CameraMode.Navigation -> state.copy(
                    title = context.getString(R.string.camera_mode_navigation_title),
                    summary = context.getString(R.string.camera_navigation_summary),
                    statusMessage = context.getString(R.string.camera_mode_navigation_starting),
                    currentMode = mode,
                    currencyDisplay = "",
                    currencyConfidence = 0f,
                )

                CameraMode.OCR -> state.copy(
                    title = context.getString(R.string.camera_mode_ocr_title),
                    summary = context.getString(R.string.camera_ocr_summary),
                    statusMessage = context.getString(R.string.camera_mode_ocr_starting),
                    currentMode = mode,
                    currencyDisplay = "",
                    currencyConfidence = 0f,
                )

                CameraMode.Currency -> state.copy(
                    title = context.getString(R.string.camera_mode_currency_title),
                    summary = context.getString(R.string.camera_currency_summary),
                    statusMessage = context.getString(R.string.currency_instruction),
                    currentMode = mode,
                    currencyDisplay = "",
                    currencyConfidence = 0f,
                )
            }
        }

        tts.speak(context.getString(R.string.camera_mode_changed, _uiState.value.title))
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mode = _uiState.value.currentMode

        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (mode) {
                    CameraMode.Navigation -> processNavigation(imageProxy)
                    CameraMode.OCR -> processOCR(imageProxy)
                    CameraMode.Currency -> processCurrency(imageProxy)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot process camera frame", e)
                _uiState.update { it.copy(statusMessage = context.getString(R.string.camera_processing_error)) }
                imageProxy.close()
            } finally {
                isProcessingFrame.set(false)
            }
        }
    }

    private fun processNavigation(imageProxy: ImageProxy) {
        imageProxy.close()
        _uiState.update { it.copy(statusMessage = context.getString(R.string.camera_navigation_tracking)) }
    }

    private fun processOCR(imageProxy: ImageProxy) {
        imageProxy.close()
        _uiState.update { it.copy(statusMessage = context.getString(R.string.camera_ocr_searching)) }
    }

    private fun processCurrency(imageProxy: ImageProxy) {
        val analyzer = getCurrencyAnalyzer()
        if (analyzer == null) {
            imageProxy.close()
            _uiState.update { it.copy(statusMessage = context.getString(R.string.currency_model_init_error)) }
            return
        }
        analyzer.analyze(imageProxy)
    }

    override fun onCleared() {
        currencyAnalyzer?.close()
        super.onCleared()
    }

    private fun currencyDisplay(label: String): String = when (label) {
        "1000" -> context.getString(R.string.currency_display_1000)
        "2000" -> context.getString(R.string.currency_display_2000)
        "5000" -> context.getString(R.string.currency_display_5000)
        "10000" -> context.getString(R.string.currency_display_10000)
        "20000" -> context.getString(R.string.currency_display_20000)
        "50000" -> context.getString(R.string.currency_display_50000)
        "100000" -> context.getString(R.string.currency_display_100000)
        "200000" -> context.getString(R.string.currency_display_200000)
        "500000" -> context.getString(R.string.currency_display_500000)
        else -> label
    }

    private fun currencySpokenLabel(label: String): String = when (label) {
        "1000" -> context.getString(R.string.currency_spoken_1000)
        "2000" -> context.getString(R.string.currency_spoken_2000)
        "5000" -> context.getString(R.string.currency_spoken_5000)
        "10000" -> context.getString(R.string.currency_spoken_10000)
        "20000" -> context.getString(R.string.currency_spoken_20000)
        "50000" -> context.getString(R.string.currency_spoken_50000)
        "100000" -> context.getString(R.string.currency_spoken_100000)
        "200000" -> context.getString(R.string.currency_spoken_200000)
        "500000" -> context.getString(R.string.currency_spoken_500000)
        else -> label
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
