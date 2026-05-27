package com.example.eyes.infrastructure.openai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay

internal class OpenAiHttpException(
    val statusCode: Int,
    val responseBody: String
) : IOException(
    buildString {
        append("OpenAI API HTTP ")
        append(statusCode)
        if (responseBody.isNotBlank()) {
            append(": ")
            append(responseBody)
        }
    }
)

internal open class OpenAiResponsesHttpClient(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 60_000,
    private val retryDelayMs: Long = 400L
) {

    open suspend fun postJsonWithRetry(
        endpoint: String,
        apiKey: String,
        requestBody: String,
        fallbackErrorMessage: String,
        maxAttempts: Int = 2
    ): String {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return postJson(endpoint = endpoint, apiKey = apiKey, requestBody = requestBody)
            } catch (error: Throwable) {
                lastError = error
                if (!error.isTransientNetworkError() || attempt == maxAttempts - 1) {
                    throw error
                }
                delay(retryDelayMs)
            }
        }
        throw lastError ?: IOException(fallbackErrorMessage)
    }

    private fun postJson(endpoint: String, apiKey: String, requestBody: String): String {
        val url = URL(endpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(StandardCharsets.UTF_8))
            }

            val statusCode = connection.responseCode
            when (statusCode) {
                in 200..299 -> connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                else -> {
                    val errorText = connection.errorStream
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.use { it.readText() }
                        ?: ""
                    throw OpenAiHttpException(statusCode = statusCode, responseBody = errorText)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun Throwable.isTransientNetworkError(): Boolean {
        if (this is OpenAiHttpException) return false
        return this is SocketTimeoutException || this is IOException && message?.contains("timeout", ignoreCase = true) == true
    }
}
