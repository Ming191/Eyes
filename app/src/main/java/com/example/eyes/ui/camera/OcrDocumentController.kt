package com.example.eyes.ui.camera

import com.example.eyes.application.ports.HapticFeedback
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrPostProcessor
import com.example.eyes.domain.ocr.OcrResult
import com.example.eyes.application.ports.SpeechOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class OcrDocumentController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticFeedback,
    private val cameraText: () -> CameraText,
    private val appLanguage: () -> AppLanguage
) {
    private val lastOcrSwipeAtMs = AtomicReference(0L)

    fun enterOcrDocumentMode(
        result: OcrResult,
        usedFallback: Boolean,
        canTranslateCurrentDocument: Boolean
    ) {
        val sentences = result.sentences.ifEmpty { OcrPostProcessor.splitToSentences(result.fullText) }
        val finalSentences = sentences.ifEmpty { listOf(result.fullText.trim()).filter { it.isNotBlank() } }

        if (finalSentences.isEmpty()) {
            uiState.update { it.copy(isOcrScanning = false, statusMessage = cameraText().noTextDetectedTryAgain) }
            hapticService.error()
            return
        }

        uiState.update {
            it.copy(
                isOcrScanning = false,
                ocrSentences = finalSentences,
                ocrCurrentIndex = 0,
                canTranslateCurrentOcrDocument = canTranslateCurrentDocument,
                statusMessage = if (usedFallback) {
                    cameraText().gptFallbackStatus(finalSentences.size)
                } else {
                    cameraText().capturedParagraphs(finalSentences.size)
                },
                lastAnnouncement = finalSentences.first()
            )
        }
        hapticService.confirm()
        speakCurrentOcrSentence()
    }

    fun speakCurrentOcrSentence() {
        val state = uiState.value
        if (!state.isOcrDocumentMode) return
        val sentence = state.currentOcrSentence
        val locale = if (looksEnglish(sentence)) Locale.US else VIETNAMESE_LOCALE
        speechOutput.speak(
            cameraText().ocrSentencePosition(state.ocrCurrentIndex + 1, state.ocrSentences.size, sentence),
            locale
        )
    }

    fun nextOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = uiState.value
        if (!state.hasNextOcrSentence) {
            speechOutput.speak(cameraText().endOfText, appLanguage().ttsLocale)
            return
        }
        uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex + 1) }
        speakCurrentOcrSentence()
    }

    fun prevOcrSentence() {
        if (!canHandleOcrSwipe()) return
        val state = uiState.value
        if (!state.hasPrevOcrSentence) {
            speechOutput.speak(cameraText().startOfText, appLanguage().ttsLocale)
            return
        }
        uiState.update { it.copy(ocrCurrentIndex = it.ocrCurrentIndex - 1) }
        speakCurrentOcrSentence()
    }

    fun canHandleOcrSwipe(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastOcrSwipeAtMs.get()
        if (now - last < OCR_SWIPE_DEBOUNCE_MS) return false
        lastOcrSwipeAtMs.set(now)
        return true
    }

    fun looksEnglish(text: String): Boolean {
        if (text.isBlank()) return false
        if (VI_DIACRITIC_REGEX.containsMatchIn(text)) return false
        return EN_LETTER_REGEX.containsMatchIn(text)
    }

    private companion object {
        private const val OCR_SWIPE_DEBOUNCE_MS = 320L
        private val VIETNAMESE_LOCALE: Locale = Locale.Builder().setLanguage("vi").setRegion("VN").build()
        private val VI_DIACRITIC_REGEX = Regex(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            RegexOption.IGNORE_CASE
        )
        private val EN_LETTER_REGEX = Regex("[A-Za-z]")
    }
}
