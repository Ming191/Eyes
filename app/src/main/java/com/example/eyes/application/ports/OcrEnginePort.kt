package com.example.eyes.application.ports

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.eyes.ocr.OcrResult

interface OcrEnginePort {
    suspend fun recognize(imageProxy: ImageProxy): OcrResult

    suspend fun recognize(bitmap: Bitmap): OcrResult

    fun close()
}
