package com.example.eyes.ocr

import com.example.eyes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

class GptTranslationEngine : OcrTranslator {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translateToVietnamese(text: String): String {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isBlank()) {
                throw IOException("OPENAI_API_KEY chưa được cấu hình trong .env hoặc biến môi trường")
            }

            val endpoint = BuildConfig.OPENAI_BASE_URL.ifBlank { DEFAULT_ENDPOINT }
            val model = BuildConfig.OPENAI_OCR_MODEL.ifBlank { DEFAULT_MODEL }
            val requestBody = buildRequestBody(model = model, sourceText = text)

            val rawResponse = retryTransient(maxAttempts = 2) {
                postJson(endpoint = endpoint, apiKey = apiKey, requestBody = requestBody)
            }
            val translatedText = OpenAiResponseTextExtractor.extract(rawResponse, json)

            if (translatedText.isBlank()) {
                throw IOException("GPT-4o không trả về bản dịch hợp lệ")
            }

            translatedText
        }
    }

    private fun postJson(endpoint: String, apiKey: String, requestBody: String): String {
        val url = URL(endpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
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
                401 -> throw IOException("OpenAI API trả về 401: sai API key hoặc chưa được cấp quyền")
                403 -> throw IOException("OpenAI API trả về 403: không có quyền truy cập model hoặc endpoint")
                429 -> throw IOException("OpenAI API trả về 429: vượt quota hoặc bị rate limit")
                else -> {
                    val errorText = connection.errorStream
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.use { it.readText() }
                        ?: ""
                    throw IOException("OpenAI API lỗi HTTP $statusCode: $errorText")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend inline fun <T> retryTransient(maxAttempts: Int, crossinline block: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                if (!error.isTransientNetworkError() || attempt == maxAttempts - 1) {
                    throw error
                }
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
        }
        throw lastError ?: IOException("Không thể thực hiện yêu cầu dịch")
    }

    private fun Throwable.isTransientNetworkError(): Boolean {
        return this is SocketTimeoutException || this is IOException && message?.contains("timeout", ignoreCase = true) == true
    }

    private fun buildRequestBody(model: String, sourceText: String): String {
        val systemPrompt = "Ban la cong cu dich. Neu van ban la tieng Anh, hay dich sang tieng Viet tu nhien va ro nghia. Neu khong phai tieng Anh, giu nguyen."
        val userPrompt = "Dich sang tieng Viet va chi tra ve ban dich:\n\n$sourceText"

                return """
                        {
                            "model": "$model",
                            "temperature": 0,
                            "input": [
                                {
                                    "role": "system",
                                    "content": [
                                        { "type": "input_text", "text": ${json.encodeToString(String.serializer(), systemPrompt)} }
                                    ]
                                },
                                {
                                    "role": "user",
                                    "content": [
                                        { "type": "input_text", "text": ${json.encodeToString(String.serializer(), userPrompt)} }
                                    ]
                                }
                            ]
                        }
                """.trimIndent()
    }

    private companion object {
        private const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses"
        private const val DEFAULT_MODEL = "gpt-4o"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val RETRY_DELAY_MS = 400L
    }
}
