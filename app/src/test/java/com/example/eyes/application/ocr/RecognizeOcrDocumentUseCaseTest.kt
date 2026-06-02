package com.example.eyes.application.ocr

import com.example.eyes.application.ports.OcrEnginePort
import com.example.eyes.application.ports.OcrEngineRefusalException
import com.example.eyes.application.ports.OcrTranslatorPort
import com.example.eyes.domain.image.ImageFormat
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.ocr.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizeOcrDocumentUseCaseTest {

    @Test
    fun invoke_accuracyMode_acceptsScannedTextThatLooksLikeRefusal() = runBlocking {
        val scannedText = "I'm sorry, I can't assist with that request."
        val useCase = RecognizeOcrDocumentUseCase(
            quickOcrEngine = FakeOcrEngine(OcrResult("quick result", listOf("quick result"))),
            accuracyOcrEngine = FakeOcrEngine(OcrResult(scannedText, listOf(scannedText))),
            translator = FakeTranslator()
        )

        val result = useCase(input(mode = OcrMode.ACCURACY))

        assertEquals(scannedText, result.rawResult.fullText)
        assertFalse(result.usedFallbackFromAccuracy)
        assertEquals(null, result.fallbackReason)
    }

    @Test
    fun invoke_accuracyMode_fallsBackWhenEngineThrowsRefusalException() = runBlocking {
        val useCase = RecognizeOcrDocumentUseCase(
            quickOcrEngine = FakeOcrEngine(OcrResult("quick result", listOf("quick result"))),
            accuracyOcrEngine = FakeOcrEngine(error = OcrEngineRefusalException("refused")),
            translator = FakeTranslator()
        )

        val result = useCase(input(mode = OcrMode.ACCURACY))

        assertEquals("quick result", result.rawResult.fullText)
        assertTrue(result.usedFallbackFromAccuracy)
        assertEquals(OcrFallbackReason.GptRefused, result.fallbackReason)
    }

    private fun input(mode: OcrMode): RecognizeOcrDocumentInput = RecognizeOcrDocumentInput(
        imageFrame = ImageFrame(
            data = byteArrayOf(1),
            width = 1,
            height = 1,
            format = ImageFormat.UNKNOWN
        ),
        mode = mode,
        translateToVietnamese = false
    )

    private class FakeOcrEngine(
        private val result: OcrResult? = null,
        private val error: Throwable? = null
    ) : OcrEnginePort {
        override suspend fun recognize(imageFrame: ImageFrame): OcrResult {
            error?.let { throw it }
            return checkNotNull(result)
        }

        override fun close() = Unit
    }

    private class FakeTranslator : OcrTranslatorPort {
        override suspend fun translateToVietnamese(text: String): String = text
    }
}
