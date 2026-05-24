package com.example.eyes.ocr

interface OcrTranslator {
    suspend fun translateToVietnamese(text: String): String
}
