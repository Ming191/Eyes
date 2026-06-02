package com.example.eyes.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGuidanceTrackerTest {
    @Test
    fun updateStabilityCountsStableFramesAndResetsOnNull() {
        val tracker = OcrGuidanceTracker(stableCenterDelta = 0.12f, stableAreaDelta = 0.15f)
        val first = OcrTextBounds(0.1f, 0.1f, 0.5f, 0.5f)
        val stable = OcrTextBounds(0.11f, 0.1f, 0.51f, 0.5f)

        assertEquals(1, tracker.updateStability(first))
        assertEquals(2, tracker.updateStability(stable))
        assertEquals(0, tracker.updateStability(null))
        assertEquals(1, tracker.updateStability(stable))
    }

    @Test
    fun updateStabilityRestartsWhenBoundsMoveTooFar() {
        val tracker = OcrGuidanceTracker(stableCenterDelta = 0.05f, stableAreaDelta = 0.15f)

        assertEquals(1, tracker.updateStability(OcrTextBounds(0f, 0f, 0.2f, 0.2f)))
        assertEquals(1, tracker.updateStability(OcrTextBounds(0.5f, 0.5f, 0.7f, 0.7f)))
    }

    @Test
    fun shouldAnnounceOnlyReadyOnceUntilResetByNonReady() {
        val tracker = OcrGuidanceTracker(speechIntervalMs = 100L)

        assertTrue(tracker.shouldAnnounce(OcrGuidanceStatus.READY, nowMs = 1_000L))
        assertFalse(tracker.shouldAnnounce(OcrGuidanceStatus.READY, nowMs = 1_200L))
        assertFalse(tracker.shouldAnnounce(OcrGuidanceStatus.HOLD_STEADY, nowMs = 1_300L))
        assertTrue(tracker.shouldAnnounce(OcrGuidanceStatus.READY, nowMs = 1_301L))
    }
}
