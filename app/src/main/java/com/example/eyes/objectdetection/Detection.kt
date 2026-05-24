package com.example.eyes.objectdetection

import android.graphics.RectF

data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val position: DetectionPosition
)
