package com.example.eyes.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionTest {

    @Test
    fun isAlertCandidate_acceptsReliableNonPriorityLabel() {
        // GIVEN
        val detection = sampleDetection(
            labelEn = "backpack",
            confidence = 0.86f
        )

        // WHEN / THEN
        assertFalse(detection.isPriority())
        assertTrue(detection.hasReliableLabel())
        assertTrue(detection.isAlertCandidate())
    }

    @Test
    fun isAlertCandidate_rejectsLowConfidenceNonPriorityLabel() {
        // GIVEN
        val detection = sampleDetection(
            labelEn = "backpack",
            confidence = 0.79f
        )

        // WHEN / THEN
        assertFalse(detection.isPriority())
        assertFalse(detection.hasReliableLabel())
        assertFalse(detection.isAlertCandidate())
    }

    @Test
    fun isNearby_withMidasDepthUnset_usesBBoxThresholdAtSensitivityZero() {
        // GIVEN
        val near = sampleDetection(bboxDepthScore = 0.52f)
        val far = sampleDetection(bboxDepthScore = 0.519f)

        // WHEN / THEN
        assertTrue(near.isNearby(alertSensitivity = 0f))
        assertFalse(far.isNearby(alertSensitivity = 0f))
    }

    @Test
    fun isNearby_withMidasDepthUnset_usesBBoxThresholdAtMiddleSensitivity() {
        // GIVEN
        val near = sampleDetection(bboxDepthScore = 0.395f)
        val far = sampleDetection(bboxDepthScore = 0.394f)

        // WHEN / THEN
        assertTrue(near.isNearby(alertSensitivity = 0.5f))
        assertFalse(far.isNearby(alertSensitivity = 0.5f))
    }

    @Test
    fun isNearby_withMidasDepthUnset_usesBBoxThresholdAtSensitivityOne() {
        // GIVEN
        val near = sampleDetection(bboxDepthScore = 0.27f)
        val far = sampleDetection(bboxDepthScore = 0.269f)

        // WHEN / THEN
        assertTrue(near.isNearby(alertSensitivity = 1f))
        assertFalse(far.isNearby(alertSensitivity = 1f))
    }

    @Test
    fun isNearby_whenBothDepthsSet_prefersMidasDepth() {
        // GIVEN
        val detection = sampleDetection(
            bboxDepthScore = 1f,
            midasDepth = 0.599f
        )

        // WHEN / THEN
        assertFalse(detection.isNearby(alertSensitivity = 0f))
    }

    @Test
    fun isNearby_withMidasDepthSet_usesMidasThresholdAtBoundary() {
        // GIVEN
        val near = sampleDetection(bboxDepthScore = 0f, midasDepth = 0.60f)
        val far = sampleDetection(bboxDepthScore = 1f, midasDepth = 0.599f)

        // WHEN / THEN
        assertTrue(near.isNearby(alertSensitivity = 0f))
        assertFalse(far.isNearby(alertSensitivity = 0f))
    }

    @Test
    fun isNearby_withNonPositiveMidasDepth_fallsBackToBBoxScore() {
        // GIVEN
        val defaultUnset = sampleDetection(bboxDepthScore = 0.52f)
        val zeroDepth = sampleDetection(bboxDepthScore = 0.52f, midasDepth = 0f)

        // WHEN / THEN
        assertTrue(defaultUnset.isNearby(alertSensitivity = 0f))
        assertTrue(zeroDepth.isNearby(alertSensitivity = 0f))
    }

    private fun sampleDetection(
        labelEn: String = "backpack",
        confidence: Float = 0.9f,
        bboxDepthScore: Float = 0.7f,
        midasDepth: Float = -1f
    ): Detection {
        return Detection(
            labelEn = labelEn,
            labelVi = "ba lô",
            bbox = BBox(0.2f, 0.2f, 0.6f, 0.8f),
            confidence = confidence,
            zone = Zone.CENTER,
            bboxDepthScore = bboxDepthScore,
            midasDepth = midasDepth
        )
    }
}
