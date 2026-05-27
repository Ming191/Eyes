package com.example.eyes.ocr

import com.example.eyes.BuildConfig
import com.example.eyes.application.ports.OcrTranslatorPort
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class GptTranslationEngine : OcrTranslatorPort {
    private val httpClient = OpenAiResponsesHttpClient()
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

            val rawResponse = httpClient.postJsonWithRetry(
                endpoint = endpoint,
                apiKey = apiKey,
                requestBody = requestBody,
                fallbackErrorMessage = "Không thể thực hiện yêu cầu dịch"
            )
            val translatedText = OpenAiResponseTextExtractor.extract(rawResponse, json)

            if (translatedText.isBlank()) {
                throw IOException("GPT-4o không trả về bản dịch hợp lệ")
            }

            translatedText
        }
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
    }
}
