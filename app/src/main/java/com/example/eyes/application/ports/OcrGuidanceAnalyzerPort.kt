package com.example.eyes.application.ports

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.ocr.OcrGuidanceFrame

interface OcrGuidanceAnalyzerPort : AutoCloseable {
    suspend fun analyze(imageFrame: ImageFrame): OcrGuidanceFrame
    override fun close()
}
