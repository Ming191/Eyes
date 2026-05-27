package com.example.eyes.application.ports

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.objectdetection.Detection
import com.example.eyes.objectdetection.YoloOutputInfo

interface ObjectDetectorPort {
    suspend fun inspectOutputShape(): List<YoloOutputInfo>

    suspend fun detect(imageFrame: ImageFrame): List<Detection>
}
