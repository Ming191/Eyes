package com.example.eyes.ai

import android.graphics.RectF
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

    private fun sampleDetection(labelEn: String, confidence: Float): Detection {
        return Detection(
            labelEn = labelEn,
            labelVi = "ba lô",
            bbox = RectF(0.2f, 0.2f, 0.6f, 0.8f),
            confidence = confidence,
            zone = Zone.CENTER,
            bboxDepthScore = 0.7f
        )
    }
}
