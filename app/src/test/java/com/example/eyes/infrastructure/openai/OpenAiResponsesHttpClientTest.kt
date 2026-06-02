package com.example.eyes.infrastructure.openai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiResponsesHttpClientTest {

    @Test
    fun postJsonWithRetry_sendsHeadersAndBody() = runTest {
        val connection = FakeHttpURLConnection(statusCode = 200, responseBody = """{ "output_text": "ok" }""")
        val client = OpenAiResponsesHttpClient(retryDelayMs = 0L, connectionFactory = { connection })

        val result = client.postJsonWithRetry(
            endpoint = "https://local.test/responses",
            apiKey = "local-key",
            requestBody = """{ "hello": "world" }""",
            fallbackErrorMessage = "failed"
        )

        assertEquals("""{ "output_text": "ok" }""", result)
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer local-key", connection.headers["Authorization"])
        assertEquals("application/json", connection.headers["Content-Type"])
        assertEquals("""{ "hello": "world" }""", connection.output.toString(Charsets.UTF_8.name()))
        assertTrue(connection.disconnected)
    }

    @Test
    fun postJsonWithRetry_nonSuccessThrowsHttpException() = runTest {
        val connection = FakeHttpURLConnection(statusCode = 429, errorBody = "rate limited")
        val client = OpenAiResponsesHttpClient(retryDelayMs = 0L, connectionFactory = { connection })
            try {
                client.postJsonWithRetry(
                    endpoint = "https://local.test/responses",
                    apiKey = "local-key",
                    requestBody = "{}",
                    fallbackErrorMessage = "failed"
                )
            } catch (error: OpenAiHttpException) {
                assertEquals(429, error.statusCode)
                assertTrue(error.responseBody.contains("rate limited"))
                return@runTest
            }
            throw AssertionError("Expected OpenAiHttpException")
    }

    private class FakeHttpURLConnection(
        statusCode: Int,
        responseBody: String = "",
        errorBody: String = ""
    ) : HttpURLConnection(URL("https://local.test/responses")) {
        val headers = mutableMapOf<String, String>()
        val output = ByteArrayOutputStream()
        var disconnected = false

        private val response = ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))
        private val error = ByteArrayInputStream(errorBody.toByteArray(Charsets.UTF_8))

        init { responseCode = statusCode }

        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        override fun getOutputStream(): ByteArrayOutputStream = output
        override fun getInputStream(): InputStream = response
        override fun getErrorStream(): InputStream = error
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
