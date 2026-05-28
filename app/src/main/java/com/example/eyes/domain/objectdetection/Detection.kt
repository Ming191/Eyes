package com.example.eyes.domain.objectdetection

data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: DetectionBounds,
    val position: DetectionPosition
)

data class DetectionBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
