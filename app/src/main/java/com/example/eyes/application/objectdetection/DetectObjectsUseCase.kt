package com.example.eyes.application.objectdetection

import android.graphics.Bitmap
import com.example.eyes.application.ports.ObjectDetectorPort
import com.example.eyes.objectdetection.Detection

class DetectObjectsUseCase(
    private val objectDetector: ObjectDetectorPort
) {
    suspend operator fun invoke(bitmap: Bitmap): List<Detection> {
        return objectDetector.detect(bitmap)
    }
}
