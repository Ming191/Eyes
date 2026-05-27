package com.example.eyes.domain.scene

sealed interface SceneDescription {
    data class Success(val text: String) : SceneDescription
    data class Failure(val error: SceneDescriptionError) : SceneDescription
}

enum class SceneDescriptionError {
    OFFLINE,
    API_KEY_MISSING,
    UNAUTHORIZED,
    RATE_LIMIT,
    TIMEOUT,
    EMPTY_RESPONSE,
    UNKNOWN
}
