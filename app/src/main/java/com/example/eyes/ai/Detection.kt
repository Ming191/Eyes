package com.example.eyes.ai

data class BBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class Detection(
    val labelEn: String,
    val labelVi: String,
    val bbox: BBox,
    val confidence: Float,
    val zone: Zone,
    val bboxDepthScore: Float,
    var midasDepth: Float = -1f
) {
    fun isPriority(): Boolean = labelEn in PRIORITY_LABELS

    fun hasReliableLabel(): Boolean = confidence >= RELIABLE_LABEL_CONFIDENCE

    fun isAlertCandidate(): Boolean = isPriority() || hasReliableLabel()

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
private const val RELIABLE_LABEL_CONFIDENCE = 0.80f
