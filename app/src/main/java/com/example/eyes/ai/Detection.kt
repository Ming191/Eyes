package com.example.eyes.ai

import android.graphics.RectF

data class Detection(
    val labelEn: String,
    val labelVi: String,
    val bbox: RectF,
    val confidence: Float,
    val zone: Zone,
    val bboxDepthScore: Float,
    var midasDepth: Float = -1f
) {
    fun isPriority(): Boolean = labelEn in PRIORITY_LABELS

    fun isNearby(alertSensitivity: Float): Boolean {
        val normalizedSensitivity = alertSensitivity.coerceIn(0f, 1f)
        val midasThreshold = MIDAS_NEAR_BASE - (normalizedSensitivity * 0.25f)
        val bboxThreshold = BBOX_NEAR_BASE - (normalizedSensitivity * 0.25f)
        return if (midasDepth > 0f) {
            midasDepth >= midasThreshold
        } else {
            bboxDepthScore >= bboxThreshold
        }
    }
}

val PRIORITY_LABELS = setOf(
    "person",
    "motorcycle",
    "bicycle",
    "car",
    "bus",
    "truck",
    "traffic light",
    "stop sign",
    "bench"
)

private const val MIDAS_NEAR_BASE = 0.60f
private const val BBOX_NEAR_BASE = 0.52f
