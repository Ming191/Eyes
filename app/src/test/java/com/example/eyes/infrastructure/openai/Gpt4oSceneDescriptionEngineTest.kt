package com.example.eyes.infrastructure.openai

import android.graphics.Bitmap
import com.example.eyes.data.remote.SceneDescriptionEngineException
import com.example.eyes.data.remote.SceneDescriptionErrorType
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.camera.toImageFrame
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Gpt4oSceneDescriptionEngineTest {

    @Test
    fun describe_postsImageRequestAndNormalizesResponse() = runTest {
        val httpClient = CapturingOpenAiClient("""{ "output_text": "  Path   clear\n ahead. " }""")
        val engine = Gpt4oSceneDescriptionEngine(
            httpClient = httpClient,
            apiKeyProvider = { "test-key" },
            endpointProvider = { "https://local.test/responses" },
            modelProvider = { "test-model" }
        )
        val frame = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).toImageFrame()

        val result = engine.describe(frame, AppLanguage.EN)

        assertEquals("Path clear ahead.", result)
        assertTrue(httpClient.requestBody.contains("\"model\": \"test-model\""))
        assertTrue(httpClient.requestBody.contains("input_image"))
        assertTrue(httpClient.requestBody.contains("Describe this image"))
    }

    @Test
    fun describe_blankApiKeyThrowsTypedExceptionBeforeNetwork() = runTest {
        val engine = Gpt4oSceneDescriptionEngine(
            httpClient = CapturingOpenAiClient("""{ "output_text": "unused" }"""),
            apiKeyProvider = { "" }
        )
        val frame = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).toImageFrame()

        try {
            engine.describe(frame, AppLanguage.VI)
        } catch (error: SceneDescriptionEngineException) {
            assertEquals(SceneDescriptionErrorType.API_KEY_MISSING, error.type)
            return@runTest
        }
        throw AssertionError("Expected SceneDescriptionEngineException")
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
