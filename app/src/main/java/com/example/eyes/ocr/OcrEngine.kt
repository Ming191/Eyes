package com.example.eyes.ocr

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

interface OcrEngine {
    suspend fun recognize(imageProxy: ImageProxy): OcrResult

    suspend fun recognize(bitmap: Bitmap): OcrResult

    fun close()
}
