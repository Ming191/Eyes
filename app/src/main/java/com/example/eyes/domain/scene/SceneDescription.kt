package com.example.eyes.domain.scene

sealed interface SceneDescription {
    data class Success(val text: String) : SceneDescription
    data class Failure(val userMessage: String) : SceneDescription
}
