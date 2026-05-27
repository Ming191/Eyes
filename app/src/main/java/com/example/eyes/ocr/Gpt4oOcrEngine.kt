package com.example.eyes.ocr

import android.graphics.Bitmap
import android.util.Base64
import androidx.camera.core.ImageProxy
import com.example.eyes.BuildConfig
import com.example.eyes.application.ports.OcrEnginePort
import com.example.eyes.camera.toBitmapWithRotation
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Gpt4oOcrEngine : OcrEnginePort {
    private val httpClient = OpenAiResponsesHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun recognize(imageProxy: ImageProxy): OcrResult {
        return try {
            val bitmap = imageProxy.toBitmapWithRotation()
            recognize(bitmap)
        } finally {
            imageProxy.close()
        }
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isBlank()) {
                throw IOException("OPENAI_API_KEY chưa được cấu hình trong .env hoặc biến môi trường")
            }

            val endpoint = BuildConfig.OPENAI_BASE_URL.ifBlank { DEFAULT_ENDPOINT }
            val model = BuildConfig.OPENAI_OCR_MODEL.ifBlank { DEFAULT_MODEL }
            val imageDataUrl = bitmap.toDataUrl()
            val requestBody = buildRequestBody(model = model, imageDataUrl = imageDataUrl)

            val rawResponse = httpClient.postJsonWithRetry(
                endpoint = endpoint,
                apiKey = apiKey,
                requestBody = requestBody,
                fallbackErrorMessage = "Không thể thực hiện yêu cầu OCR"
            )
            val extractedText = OpenAiResponseTextExtractor.extract(rawResponse, json)

            if (extractedText.isBlank()) {
                throw IOException("GPT-4o không trả về văn bản hợp lệ")
            }

            OcrPostProcessor.process(extractedText)
        }
    }

    override fun close() = Unit

    private fun buildRequestBody(model: String, imageDataUrl: String): String {
        val systemPrompt = "Bạn là OCR đa ngôn ngữ (Việt/Anh). Trích xuất văn bản nguyên bản, giữ nguyên dấu, ký tự, xuống dòng. Không diễn giải thêm."
        val userPrompt = "Hãy trả về toàn bộ văn bản nhìn thấy trong ảnh. Chỉ trả về văn bản gốc."

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
                    { "type": "input_text", "text": ${json.encodeToString(String.serializer(), userPrompt)} },
                    { "type": "input_image", "image_url": "$imageDataUrl" }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun Bitmap.toDataUrl(): String {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private companion object {
        private const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses"
        private const val DEFAULT_MODEL = "gpt-4o"
        private const val JPEG_QUALITY = 95
    }
}
