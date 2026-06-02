package com.example.eyes.infrastructure.openai

import com.example.eyes.application.ports.OcrEngineRefusalException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GptOcrOutputParserTest {

    @Test
    fun parse_removesOcrTextPrefix() {
        val result = GptOcrOutputParser.parse(
            """
                OCR_TEXT:
                Hello world
                This is an English document.
            """.trimIndent()
        )

        assertEquals("Hello world\nThis is an English document.", result)
    }

    @Test
    fun parse_acceptsPrefixedScannedTextThatLooksLikeRefusal() {
        val scannedText = "I'm sorry, I can't assist with that request."

        val result = GptOcrOutputParser.parse("OCR_TEXT:\n$scannedText")

        assertEquals(scannedText, result)
    }

    @Test
    fun parse_throwsWhenModelReturnsFreeTextRefusal() {
        val error = assertThrows(OcrEngineRefusalException::class.java) {
            GptOcrOutputParser.parse("I'm sorry, I can't assist with that request.")
        }

        assertEquals("I'm sorry, I can't assist with that request.", error.message)
    }

    @Test
    fun parse_noTextDetectedReturnsBlank() {
        assertEquals("", GptOcrOutputParser.parse("NO_TEXT_DETECTED"))
    }
}
