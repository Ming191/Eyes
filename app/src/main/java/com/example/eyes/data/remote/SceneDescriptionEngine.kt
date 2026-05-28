package com.example.eyes.data.remote

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.i18n.AppLanguage
import java.io.IOException

interface SceneDescriptionEngine {
    @Throws(SceneDescriptionEngineException::class)
    suspend fun describe(imageFrame: ImageFrame, language: AppLanguage): String
}

enum class SceneDescriptionErrorType {
    API_KEY_MISSING,
    UNAUTHORIZED,
    RATE_LIMIT,
    TIMEOUT,
    EMPTY_RESPONSE,
    UNKNOWN
}

class SceneDescriptionEngineException(
    val type: SceneDescriptionErrorType,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
