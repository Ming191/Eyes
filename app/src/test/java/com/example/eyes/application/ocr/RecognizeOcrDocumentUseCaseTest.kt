package com.example.eyes.application.ocr

import com.example.eyes.application.ports.OcrEnginePort
import com.example.eyes.application.ports.OcrTranslatorPort
import com.example.eyes.domain.image.ImageFormat
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.ocr.OcrResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizeOcrDocumentUseCaseTest {
    private val frame = ImageFrame(byteArrayOf(1), 1, 1, ImageFormat.JPEG)

    @Test
    fun invoke_quickMode_usesQuickEngineWithoutFallback() = runTest {
        val quick = FakeOcrEngine(OcrResult("Hello world", listOf("Hello world")))
        val useCase = RecognizeOcrDocumentUseCase(quick, FakeOcrEngine(OcrResult.EMPTY), FakeTranslator())

        val result = useCase(RecognizeOcrDocumentInput(frame, OcrMode.QUICK, translateToVietnamese = false))

        assertEquals("Hello world", result.rawResult.fullText)
        assertEquals("Hello world", result.resultForSpeech.fullText)
        assertFalse(result.usedFallbackFromAccuracy)
        assertTrue(result.canTranslateDocument)
        assertEquals(1, quick.calls)
    }

    @Test
    fun invoke_accuracyRefusal_fallsBackToQuickWithReason() = runTest {
        val quick = FakeOcrEngine(OcrResult("Fallback text", listOf("Fallback text")))
        val accuracy = FakeOcrEngine(OcrResult("I'm sorry, I can't assist with that", emptyList()))
        val useCase = RecognizeOcrDocumentUseCase(quick, accuracy, FakeTranslator())

        val result = useCase(RecognizeOcrDocumentInput(frame, OcrMode.ACCURACY, translateToVietnamese = false))

        assertEquals("Fallback text", result.rawResult.fullText)
        assertTrue(result.usedFallbackFromAccuracy)
        assertEquals(OcrFallbackReason.GptRefused, result.fallbackReason)
    }

    @Test
    fun invoke_accuracyError_mapsFallbackReasonAndUsesQuick() = runTest {
        val quick = FakeOcrEngine(OcrResult("Quick text", listOf("Quick text")))
        val accuracy = FakeOcrEngine(error = IllegalStateException("429 quota"))
        val useCase = RecognizeOcrDocumentUseCase(quick, accuracy, FakeTranslator())

        val result = useCase(RecognizeOcrDocumentInput(frame, OcrMode.ACCURACY, translateToVietnamese = false))

        assertEquals("Quick text", result.rawResult.fullText)
        assertEquals(OcrFallbackReason.Quota, result.fallbackReason)
        assertTrue(result.usedFallbackFromAccuracy)
    }

    @Test
    fun invoke_translateEnabled_translatesEnglishForSpeech() = runTest {
        val quick = FakeOcrEngine(OcrResult("This is an English document", listOf("This is an English document")))
        val translator = FakeTranslator("Đây là tài liệu tiếng Việt")
        val useCase = RecognizeOcrDocumentUseCase(quick, FakeOcrEngine(OcrResult.EMPTY), translator)

        val result = useCase(RecognizeOcrDocumentInput(frame, OcrMode.QUICK, translateToVietnamese = true))

        assertEquals("This is an English document", result.rawResult.fullText)
        assertEquals("Đây là tài liệu tiếng Việt", result.resultForSpeech.fullText)
        assertFalse(result.translationFailed)
    }

    @Test
    fun invoke_translationUnchanged_fallsBackToSourceAndMarksFailure() = runTest {
        val quick = FakeOcrEngine(OcrResult("This is an English document", listOf("This is an English document")))
        val translator = FakeTranslator("This is an English document")
        val useCase = RecognizeOcrDocumentUseCase(quick, FakeOcrEngine(OcrResult.EMPTY), translator)

        val result = useCase(RecognizeOcrDocumentInput(frame, OcrMode.QUICK, translateToVietnamese = true))

        assertEquals("This is an English document", result.resultForSpeech.fullText)
        assertTrue(result.translationFailed)
    }

    @Test
    fun close_closesBothEngines() {
        val quick = FakeOcrEngine(OcrResult.EMPTY)
        val accuracy = FakeOcrEngine(OcrResult.EMPTY)
        val useCase = RecognizeOcrDocumentUseCase(quick, accuracy, FakeTranslator())

        useCase.close()

        assertTrue(quick.closed)
        assertTrue(accuracy.closed)
    }

    private class FakeOcrEngine(
        private val result: OcrResult? = null,
        private val error: Throwable? = null
    ) : OcrEnginePort {
        var calls = 0
        var closed = false

        override suspend fun recognize(imageFrame: ImageFrame): OcrResult {
            calls++
            error?.let { throw it }
            return result ?: OcrResult.EMPTY
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeTranslator(private val translated: String = "") : OcrTranslatorPort {
        override suspend fun translateToVietnamese(text: String): String = translated
    }
}
