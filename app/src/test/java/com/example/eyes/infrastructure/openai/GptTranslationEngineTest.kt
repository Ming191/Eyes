package com.example.eyes.infrastructure.openai

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GptTranslationEngineTest {

    @Test
    fun translateToVietnamese_postsExpectedRequestAndReturnsExtractedText() = runTest {
        val httpClient = CapturingOpenAiClient("""{ "output_text": "Xin chao" }""")
        val engine = GptTranslationEngine(
            httpClient = httpClient,
            apiKeyProvider = { "test-key" },
            endpointProvider = { "https://local.test/responses" },
            modelProvider = { "test-model" }
        )

        val result = engine.translateToVietnamese("Hello")

        assertEquals("Xin chao", result)
        assertEquals("https://local.test/responses", httpClient.endpoint)
        assertEquals("test-key", httpClient.apiKey)
        assertTrue(httpClient.requestBody.contains("\"model\": \"test-model\""))
        assertTrue(httpClient.requestBody.contains("Dich sang tieng Viet"))
        assertTrue(httpClient.requestBody.contains("Hello"))
    }

    @Test(expected = IOException::class)
    fun translateToVietnamese_blankApiKeyThrowsBeforeNetwork() = runTest {
        val httpClient = CapturingOpenAiClient("""{ "output_text": "unused" }""")
        val engine = GptTranslationEngine(
            httpClient = httpClient,
            apiKeyProvider = { "" }
        )

        engine.translateToVietnamese("Hello")
    }

    private class CapturingOpenAiClient(
        private val response: String
    ) : OpenAiResponsesHttpClient(retryDelayMs = 0L) {
        lateinit var endpoint: String
        lateinit var apiKey: String
        lateinit var requestBody: String

        override suspend fun postJsonWithRetry(
            endpoint: String,
            apiKey: String,
            requestBody: String,
            fallbackErrorMessage: String,
            maxAttempts: Int
        ): String {
            this.endpoint = endpoint
            this.apiKey = apiKey
            this.requestBody = requestBody
            return response
        }
    }
}
