package com.example.eyes.application.ports

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.ocr.OcrResult

interface OcrEnginePort {
    suspend fun recognize(imageFrame: ImageFrame): OcrResult

    fun close()
}
