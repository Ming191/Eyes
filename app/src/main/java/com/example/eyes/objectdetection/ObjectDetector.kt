package com.example.eyes.objectdetection

import android.graphics.Bitmap

interface ObjectDetector {
    suspend fun inspectOutputShape(): List<YoloOutputInfo>

    suspend fun detect(bitmap: Bitmap): List<Detection>
}
