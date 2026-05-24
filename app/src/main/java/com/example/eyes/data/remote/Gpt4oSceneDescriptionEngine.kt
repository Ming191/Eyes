package com.example.eyes.data.remote

import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.scale
import com.example.eyes.BuildConfig
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.ocr.OpenAiHttpException
import com.example.eyes.ocr.OpenAiResponseTextExtractor
import com.example.eyes.ocr.OpenAiResponsesHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal class Gpt4oSceneDescriptionEngine(
    private val httpClient: OpenAiResponsesHttpClient = OpenAiResponsesHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val apiKeyProvider: () -> String = { BuildConfig.OPENAI_API_KEY },
    private val endpointProvider: () -> String = { BuildConfig.OPENAI_BASE_URL.ifBlank { DEFAULT_ENDPOINT } },
    private val modelProvider: () -> String = { BuildConfig.OPENAI_OCR_MODEL.ifBlank { DEFAULT_MODEL } }
) : SceneDescriptionEngine {

    override suspend fun describe(bitmap: Bitmap, language: AppLanguage): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw SceneDescriptionEngineException(
                type = SceneDescriptionErrorType.API_KEY_MISSING,
                message = "OPENAI_API_KEY is blank"
            )
        }

        val preparedBitmap = bitmap.resizeForUpload(MAX_IMAGE_EDGE_PX)
        try {
            val requestBody = buildRequestBody(
                model = modelProvider(),
                imageDataUrl = preparedBitmap.toDataUrl(JPEG_QUALITY),
                language = language
            )
            val rawResponse = httpClient.postJsonWithRetry(
                endpoint = endpointProvider(),
                apiKey = apiKey,
                requestBody = requestBody,
                fallbackErrorMessage = "Failed to describe scene"
            )
            val normalizedText = OpenAiResponseTextExtractor.extract(rawResponse, json).normalizeWhitespace()
            if (normalizedText.isBlank()) {
                throw SceneDescriptionEngineException(
                    type = SceneDescriptionErrorType.EMPTY_RESPONSE,
                    message = "Scene description response is blank"
                )
            }
            normalizedText
        } catch (error: SceneDescriptionEngineException) {
            throw error
        } catch (error: Throwable) {
            throw error.toSceneDescriptionEngineException()
        } finally {
            if (preparedBitmap !== bitmap && !preparedBitmap.isRecycled) {
                preparedBitmap.recycle()
            }
        }
    }

    private fun buildRequestBody(model: String, imageDataUrl: String, language: AppLanguage): String {
        val prompts = promptsFor(language)
        return """
            {
              "model": "$model",
              "temperature": 0,
              "input": [
                {
                  "role": "system",
                  "content": [
                    { "type": "input_text", "text": ${json.encodeToString(String.serializer(), prompts.system)} }
                  ]
                },
                {
                  "role": "user",
                  "content": [
                    { "type": "input_text", "text": ${json.encodeToString(String.serializer(), prompts.user)} },
                    { "type": "input_image", "image_url": "$imageDataUrl" }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun promptsFor(language: AppLanguage): ScenePrompts {
        return when (language) {
            AppLanguage.VI -> ScenePrompts(
                system = "Bạn là trợ lý mô tả cảnh cho người khiếm thị. Ưu tiên an toàn di chuyển và không suy đoán quá mức.",
                user = "Mô tả ảnh bằng tiếng Việt trong 1-2 câu ngắn. Nêu vật cản, lối đi và vị trí tương đối (trái/phải/trước/sau). Nếu không chắc, nói rõ \"không chắc\"."
            )

            AppLanguage.EN -> ScenePrompts(
                system = "You are a scene assistant for blind users. Prioritize walking safety and avoid over-guessing.",
                user = "Describe this image in 1-2 short English sentences. Mention obstacles, free path, and relative positions (left/right/front/back). If uncertain, explicitly say \"not sure\"."
            )
        }
    }

    private fun Bitmap.resizeForUpload(maxSize: Int): Bitmap {
        val largestEdge = maxOf(width, height)
        if (largestEdge <= maxSize) return this

        val ratio = maxSize.toFloat() / largestEdge.toFloat()
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return scale(targetWidth, targetHeight)
    }

    private fun Bitmap.toDataUrl(quality: Int): String {
        val encoded = ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.JPEG, quality, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
        return "data:image/jpeg;base64,$encoded"
    }

    private fun String.normalizeWhitespace(): String = trim().replace(WHITESPACE_REGEX, " ")

    private fun Throwable.toSceneDescriptionEngineException(): SceneDescriptionEngineException {
        return when (this) {
            is OpenAiHttpException -> when (statusCode) {
                401, 403 -> SceneDescriptionEngineException(
                    type = SceneDescriptionErrorType.UNAUTHORIZED,
                    message = "OpenAI unauthorized ($statusCode)",
                    cause = this
                )

                429 -> SceneDescriptionEngineException(
                    type = SceneDescriptionErrorType.RATE_LIMIT,
                    message = "OpenAI rate limited (429)",
                    cause = this
                )

                else -> SceneDescriptionEngineException(
                    type = SceneDescriptionErrorType.UNKNOWN,
                    message = "OpenAI HTTP error $statusCode",
                    cause = this
                )
            }

            is SocketTimeoutException -> SceneDescriptionEngineException(
                type = SceneDescriptionErrorType.TIMEOUT,
                message = "Scene description request timed out",
                cause = this
            )

            is IOException -> {
                if (message?.contains("timeout", ignoreCase = true) == true) {
                    SceneDescriptionEngineException(
                        type = SceneDescriptionErrorType.TIMEOUT,
                        message = "Scene description request timed out",
                        cause = this
                    )
                } else {
                    SceneDescriptionEngineException(
                        type = SceneDescriptionErrorType.UNKNOWN,
                        message = message ?: "Unknown IO error",
                        cause = this
                    )
                }
            }

            else -> SceneDescriptionEngineException(
                type = SceneDescriptionErrorType.UNKNOWN,
                message = message ?: "Unknown error",
                cause = this
            )
        }
    }

    private data class ScenePrompts(
        val system: String,
        val user: String
    )

    private companion object {
        private const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses"
        private const val DEFAULT_MODEL = "gpt-4o"
        private const val MAX_IMAGE_EDGE_PX = 1024
        private const val JPEG_QUALITY = 85
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
