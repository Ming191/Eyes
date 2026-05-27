package com.example.eyes.application.ocr

import com.example.eyes.application.ports.OcrEnginePort
import com.example.eyes.application.ports.OcrTranslatorPort
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.ocr.OcrPostProcessor
import com.example.eyes.ocr.OcrResult

data class RecognizeOcrDocumentInput(
    val imageFrame: ImageFrame,
    val mode: OcrMode,
    val translateToVietnamese: Boolean,
    val strings: RecognizeOcrDocumentStrings,
    val onTranslateFailure: suspend () -> Unit = {}
)

data class RecognizeOcrDocumentStrings(
    val gptRefusedReason: String,
    val apiKeyReason: String,
    val modelPermissionReason: String,
    val quotaReason: String,
    val timeoutReason: String,
    val unknownReason: String,
    val unknownError: String,
    val translationUnchangedReason: String
)

data class RecognizeOcrDocumentResult(
    val rawResult: OcrResult,
    val resultForSpeech: OcrResult,
    val usedFallbackFromAccuracy: Boolean,
    val fallbackReason: String? = null,
    val canTranslateDocument: Boolean
)

class RecognizeOcrDocumentUseCase(
    private val quickOcrEngine: OcrEnginePort,
    private val accuracyOcrEngine: OcrEnginePort,
    private val translator: OcrTranslatorPort
) {
    suspend operator fun invoke(input: RecognizeOcrDocumentInput): RecognizeOcrDocumentResult {
        val outcome = recognizeByMode(input.imageFrame, input.mode, input.strings)
        val rawResult = outcome.result.getOrThrow()
        val translatedResult = maybeTranslateForSpeech(rawResult, input.translateToVietnamese, input.strings, input.onTranslateFailure)
        return RecognizeOcrDocumentResult(
            rawResult = rawResult,
            resultForSpeech = translatedResult,
            usedFallbackFromAccuracy = outcome.usedFallbackFromAccuracy,
            fallbackReason = outcome.fallbackReason,
            canTranslateDocument = looksEnglish(rawResult.fullText)
        )
    }

    fun close() {
        quickOcrEngine.close()
        accuracyOcrEngine.close()
    }

    private suspend fun recognizeByMode(
        imageFrame: ImageFrame,
        mode: OcrMode,
        strings: RecognizeOcrDocumentStrings
    ): OcrRecognitionOutcome {
        return when (mode) {
            OcrMode.QUICK -> OcrRecognitionOutcome(
                result = runCatching { quickOcrEngine.recognize(imageFrame) },
                usedFallbackFromAccuracy = false
            )
            OcrMode.ACCURACY -> {
                val accuracyResult = runCatching { accuracyOcrEngine.recognize(imageFrame) }
                val text = accuracyResult.getOrNull()?.fullText.orEmpty()
                val refused = accuracyResult.isSuccess && looksLikeGptRefusal(text)
                if (accuracyResult.isSuccess && !refused) {
                    OcrRecognitionOutcome(
                        result = accuracyResult,
                        usedFallbackFromAccuracy = false
                    )
                } else {
                    val reason = when {
                        refused -> strings.gptRefusedReason
                        accuracyResult.exceptionOrNull() != null -> buildFallbackReason(accuracyResult.exceptionOrNull()!!, strings)
                        else -> strings.unknownReason
                    }
                    OcrRecognitionOutcome(
                        result = runCatching { quickOcrEngine.recognize(imageFrame) },
                        usedFallbackFromAccuracy = true,
                        fallbackReason = reason
                    )
                }
            }
        }
    }

    private suspend fun maybeTranslateForSpeech(
        result: OcrResult,
        enabled: Boolean,
        strings: RecognizeOcrDocumentStrings,
        onTranslateFailure: suspend () -> Unit
    ): OcrResult {
        if (!enabled) return OcrPostProcessor.process(result.fullText)
        if (!shouldAutoTranslateToVietnamese(result.fullText)) return OcrPostProcessor.process(result.fullText)
        return translateToVietnameseOrFallback(result.fullText, strings, onTranslateFailure)
    }

    private fun looksLikeGptRefusal(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return true
        val refusalMarkers = listOf(
            "i'm sorry, i can't assist with that",
            "i can't assist with that",
            "i cannot assist with that",
            "i'm sorry",
            "i cannot help with that request"
        )
        return refusalMarkers.any { normalized.startsWith(it) }
    }

    private fun buildFallbackReason(error: Throwable, strings: RecognizeOcrDocumentStrings): String {
        val message = error.message?.trim().orEmpty()
        return when {
            message.contains("401") -> strings.apiKeyReason
            message.contains("403") -> strings.modelPermissionReason
            message.contains("429") -> strings.quotaReason
            message.contains("timeout", ignoreCase = true) -> strings.timeoutReason
            message.isNotBlank() -> message
            else -> error::class.simpleName ?: strings.unknownError
        }
    }

    private fun looksEnglish(text: String): Boolean {
        if (text.isBlank()) return false
        if (VI_DIACRITIC_REGEX.containsMatchIn(text)) return false
        return EN_LETTER_REGEX.containsMatchIn(text)
    }

    private fun shouldAutoTranslateToVietnamese(text: String): Boolean {
        if (text.isBlank()) return false
        val latinCount = EN_LETTER_REGEX.findAll(text).count()
        if (latinCount < 6) return false
        val viCount = VI_DIACRITIC_REGEX.findAll(text).count()
        return viCount == 0 || latinCount >= viCount * 3
    }

    private suspend fun translateToVietnameseOrFallback(
        sourceText: String,
        strings: RecognizeOcrDocumentStrings,
        onTranslateFailure: suspend () -> Unit
    ): OcrResult {
        return runCatching {
            val translated = translator.translateToVietnamese(sourceText)
            if (looksUntranslated(sourceText, translated)) {
                throw IllegalStateException(strings.translationUnchangedReason)
            }
            OcrPostProcessor.process(translated)
        }.getOrElse {
            onTranslateFailure()
            OcrPostProcessor.process(sourceText)
        }
    }

    private fun looksUntranslated(source: String, translated: String): Boolean {
        val sourceNorm = OcrPostProcessor.normalizeText(source).lowercase()
        val translatedNorm = OcrPostProcessor.normalizeText(translated).lowercase()
        if (sourceNorm.isBlank() || translatedNorm.isBlank()) return true

        val sourceWordCount = sourceNorm.split(Regex("\\s+")).count { it.isNotBlank() }
        if (sourceWordCount < 3) return false

        val similarity = OcrPostProcessor.similarityRatio(sourceNorm, translatedNorm)
        return similarity >= 0.92f && !VI_DIACRITIC_REGEX.containsMatchIn(translatedNorm)
    }

    private data class OcrRecognitionOutcome(
        val result: Result<OcrResult>,
        val usedFallbackFromAccuracy: Boolean,
        val fallbackReason: String? = null
    )

    private companion object {
        private val VI_DIACRITIC_REGEX = Regex(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            RegexOption.IGNORE_CASE
        )
        private val EN_LETTER_REGEX = Regex("[A-Za-z]")
    }
}
