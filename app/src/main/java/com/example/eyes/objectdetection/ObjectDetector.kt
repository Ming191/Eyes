package com.example.eyes.objectdetection

import android.graphics.Bitmap

interface ObjectDetector {
    suspend fun detect(bitmap: Bitmap): List<Detection>
}
