package com.example.eyes.infrastructure.openai

import com.example.eyes.application.ports.OcrEngineRefusalException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiResponseTextExtractorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun extract_prefersOutputTextWhenAvailable() {
        val payload = """
            {
              "output_text": "Van ban uu tien"
            }
        """.trimIndent()

        val result = OpenAiResponseTextExtractor.extract(payload, json)

        assertEquals("Van ban uu tien", result)
    }

    @Test
    fun extract_fallsBackToOutputContentTextNodes() {
        val payload = """
            {
              "output": [
                {
                  "type": "message",
                  "content": [
                    { "type": "output_text", "text": "Dong thu nhat" },
                    { "type": "output_text", "text": "Dong thu hai" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = OpenAiResponseTextExtractor.extract(payload, json)

        assertEquals("Dong thu nhat\nDong thu hai", result)
    }

    @Test
    fun extract_returnsEmptyStringWhenNoTextFound() {
        val payload = """
            {
              "output": [
                {
                  "type": "message",
                  "content": []
                }
              ]
            }
        """.trimIndent()

        val result = OpenAiResponseTextExtractor.extract(payload, json)

        assertEquals("", result)
    }

    @Test
    fun extract_throwsWhenResponseContainsStructuredRefusal() {
        val payload = """
            {
              "output": [
                {
                  "type": "message",
                  "content": [
                    { "type": "refusal", "refusal": "I can't assist with that request." }
                  ]
                }
              ]
            }
        """.trimIndent()

        val error = assertThrows(OcrEngineRefusalException::class.java) {
            OpenAiResponseTextExtractor.extract(payload, json)
        }

        assertEquals("I can't assist with that request.", error.message)
    }
}
