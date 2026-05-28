package com.example.eyes.application.ports

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.objectdetection.Detection
import com.example.eyes.domain.objectdetection.YoloOutputInfo

interface ObjectDetectorPort {
    suspend fun inspectOutputShape(): List<YoloOutputInfo>

    suspend fun detect(imageFrame: ImageFrame): List<Detection>
}
