package com.example.eyes.application.ports

interface OcrTranslatorPort {
    suspend fun translateToVietnamese(text: String): String
}
