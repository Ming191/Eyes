package com.example.eyes.application.ports

import android.graphics.Bitmap
import com.example.eyes.objectdetection.Detection
import com.example.eyes.objectdetection.YoloOutputInfo

interface ObjectDetectorPort {
    suspend fun inspectOutputShape(): List<YoloOutputInfo>

    suspend fun detect(bitmap: Bitmap): List<Detection>
}
