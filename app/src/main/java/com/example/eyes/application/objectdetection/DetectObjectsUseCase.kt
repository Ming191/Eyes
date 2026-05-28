package com.example.eyes.application.objectdetection

import com.example.eyes.application.ports.ObjectDetectorPort
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.objectdetection.Detection

class DetectObjectsUseCase(
    private val objectDetector: ObjectDetectorPort
) {
    suspend operator fun invoke(imageFrame: ImageFrame): List<Detection> {
        return objectDetector.detect(imageFrame)
    }
}
