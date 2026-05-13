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
    /**
 * Checks whether this detection's English label is classified as a priority.
 *
 * @return `true` if the `labelEn` is contained in `PRIORITY_LABELS`, `false` otherwise.
 */
fun isPriority(): Boolean = labelEn in PRIORITY_LABELS

    /**
 * Determines whether this detection's label is considered reliable.
 *
 * @return `true` if confidence is greater than or equal to the reliable label confidence threshold, `false` otherwise.
 */
fun hasReliableLabel(): Boolean = confidence >= RELIABLE_LABEL_CONFIDENCE

    /**
 * Indicates whether the detection should be considered for alerts based on label priority or confidence.
 *
 * @return `true` if the detection's label is in the priority labels set or its confidence is greater than or equal to RELIABLE_LABEL_CONFIDENCE, `false` otherwise.
 */
fun isAlertCandidate(): Boolean = isPriority() || hasReliableLabel()

    /**
     * Determines whether this detection is considered nearby given an alert sensitivity.
     *
     * Sensitivity is clamped to the range 0..1; higher sensitivity makes the near threshold more permissive.
     * If a positive MIDAS depth is available it is used for the proximity check, otherwise the bounding-box depth score is used.
     *
     * @param alertSensitivity Sensitivity in the range 0..1 (values outside this range are clamped).
     * @return `true` if the detection is considered nearby, `false` otherwise.
     */
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
