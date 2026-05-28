package com.example.eyes.application.ocr

import com.example.eyes.application.ports.OcrEnginePort
import com.example.eyes.application.ports.OcrTranslatorPort
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.ocr.OcrPostProcessor
import com.example.eyes.domain.ocr.OcrResult

data class RecognizeOcrDocumentInput(
    val imageFrame: ImageFrame,
    val mode: OcrMode,
    val translateToVietnamese: Boolean
)

sealed interface OcrFallbackReason {
    data object GptRefused : OcrFallbackReason
    data object ApiKey : OcrFallbackReason
    data object ModelPermission : OcrFallbackReason
    data object Quota : OcrFallbackReason
    data object Timeout : OcrFallbackReason
    data class EngineError(val message: String?) : OcrFallbackReason
    data object Unknown : OcrFallbackReason
}

data class RecognizeOcrDocumentResult(
    val rawResult: OcrResult,
    val resultForSpeech: OcrResult,
    val usedFallbackFromAccuracy: Boolean,
    val fallbackReason: OcrFallbackReason? = null,
    val translationFailed: Boolean = false,
    val canTranslateDocument: Boolean
)

class RecognizeOcrDocumentUseCase(
    private val quickOcrEngine: OcrEnginePort,
    private val accuracyOcrEngine: OcrEnginePort,
    private val translator: OcrTranslatorPort
) {
    suspend operator fun invoke(input: RecognizeOcrDocumentInput): RecognizeOcrDocumentResult {
        val outcome = recognizeByMode(input.imageFrame, input.mode)
        val rawResult = outcome.result.getOrThrow()
        val speechResult = maybeTranslateForSpeech(rawResult, input.translateToVietnamese)
        return RecognizeOcrDocumentResult(
            rawResult = rawResult,
            resultForSpeech = speechResult.result,
            usedFallbackFromAccuracy = outcome.usedFallbackFromAccuracy,
            fallbackReason = outcome.fallbackReason,
            translationFailed = speechResult.translationFailed,
            canTranslateDocument = looksEnglish(rawResult.fullText)
        )
    }

    fun close() {
        quickOcrEngine.close()
        accuracyOcrEngine.close()
    }

    private suspend fun recognizeByMode(
        imageFrame: ImageFrame,
        mode: OcrMode
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
                        refused -> OcrFallbackReason.GptRefused
                        accuracyResult.exceptionOrNull() != null -> buildFallbackReason(accuracyResult.exceptionOrNull()!!)
                        else -> OcrFallbackReason.Unknown
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
        enabled: Boolean
    ): OcrSpeechResult {
        if (!enabled) return OcrSpeechResult(OcrPostProcessor.process(result.fullText))
        if (!shouldAutoTranslateToVietnamese(result.fullText)) return OcrSpeechResult(OcrPostProcessor.process(result.fullText))
        return translateToVietnameseOrFallback(result.fullText)
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

    private fun buildFallbackReason(error: Throwable): OcrFallbackReason {
        val message = error.message?.trim().orEmpty()
        return when {
            message.contains("401") -> OcrFallbackReason.ApiKey
            message.contains("403") -> OcrFallbackReason.ModelPermission
            message.contains("429") -> OcrFallbackReason.Quota
            message.contains("timeout", ignoreCase = true) -> OcrFallbackReason.Timeout
            message.isNotBlank() -> OcrFallbackReason.EngineError(message)
            else -> OcrFallbackReason.EngineError(error::class.simpleName)
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
        sourceText: String
    ): OcrSpeechResult {
        return runCatching {
            val translated = translator.translateToVietnamese(sourceText)
            if (looksUntranslated(sourceText, translated)) {
                throw IllegalStateException("Translation unchanged")
            }
            OcrSpeechResult(OcrPostProcessor.process(translated))
        }.getOrElse {
            OcrSpeechResult(OcrPostProcessor.process(sourceText), translationFailed = true)
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
        val fallbackReason: OcrFallbackReason? = null
    )

    private data class OcrSpeechResult(
        val result: OcrResult,
        val translationFailed: Boolean = false
    )

    private companion object {
        private val VI_DIACRITIC_REGEX = Regex(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            RegexOption.IGNORE_CASE
        )
        private val EN_LETTER_REGEX = Regex("[A-Za-z]")
    }
}
