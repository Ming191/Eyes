package com.example.eyes.infrastructure.openai

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Gpt4oOcrEngineTest {

    @Test
    fun recognize_postsImageRequestAndReturnsProcessedOcrResult() = runTest {
        val httpClient = CapturingOpenAiClient("""{ "output_text": "Xin chào thế giới." }""")
        val engine = Gpt4oOcrEngine(
            httpClient = httpClient,
            apiKeyProvider = { "test-key" },
            endpointProvider = { "https://local.test/responses" },
            modelProvider = { "test-model" }
        )

        val result = engine.recognize(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))

        assertEquals("Xin chào thế giới.", result.fullText)
        assertEquals(listOf("Xin chào thế giới"), result.sentences)
        assertTrue(httpClient.requestBody.contains("\"model\": \"test-model\""))
        assertTrue(httpClient.requestBody.contains("input_image"))
        assertTrue(httpClient.requestBody.contains("data:image/jpeg;base64,"))
    }

    private class CapturingOpenAiClient(
        private val response: String
    ) : OpenAiResponsesHttpClient(retryDelayMs = 0L) {
        lateinit var requestBody: String

        override suspend fun postJsonWithRetry(
            endpoint: String,
            apiKey: String,
            requestBody: String,
            fallbackErrorMessage: String,
            maxAttempts: Int
        ): String {
            this.requestBody = requestBody
            return response
        }
    }
}
