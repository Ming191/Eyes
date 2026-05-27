package com.example.eyes.application.objectdetection

import com.example.eyes.application.ports.ObjectDetectorPort
import com.example.eyes.objectdetection.YoloOutputInfo

class WarmUpObjectDetectionUseCase(
    private val objectDetector: ObjectDetectorPort
) {
    suspend operator fun invoke(): List<YoloOutputInfo> {
        return objectDetector.inspectOutputShape()
    }
}
