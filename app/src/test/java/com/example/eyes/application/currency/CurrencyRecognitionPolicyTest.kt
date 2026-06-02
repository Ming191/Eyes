package com.example.eyes.application.currency

import com.example.eyes.application.ports.CurrencyRecognizerPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyRecognitionPolicyTest {
    @Test
    fun detectedResultClampsConfidenceAndSpeaksOnlyNewLabels() {
        val policy = CurrencyRecognitionPolicy(noDetectionRepeatMs = 100L)

        val first = policy.onResult("10000 VND", confidence = 1.5f, hadDisplay = false, nowMs = 1_000L)
        assertEquals("10000 VND", first.label)
        assertEquals(1f, first.safeConfidence)
        assertTrue(first.shouldSpeakDetected)
        assertFalse(first.shouldSpeakNoDetection)
        assertFalse(first.isEmpty)

        val repeated = policy.onResult("10000 VND", confidence = -1f, hadDisplay = false, nowMs = 1_001L)
        assertEquals(0f, repeated.safeConfidence)
        assertFalse(repeated.shouldSpeakDetected)

        assertTrue(policy.onResult("20000 VND", confidence = 0.5f, hadDisplay = false, nowMs = 1_002L).shouldSpeakDetected)
    }

    @Test
    fun emptyResultDebouncesNoDetectionAndResetsDisplay() {
        val policy = CurrencyRecognitionPolicy(noDetectionRepeatMs = 100L)

        val first = policy.onResult(CurrencyRecognizerPort.EMPTY_LABEL, confidence = 0.8f, hadDisplay = false, nowMs = 1_000L)
        assertNull(first.label)
        assertEquals(0f, first.safeConfidence)
        assertFalse(first.shouldResetBuffer)
        assertTrue(first.shouldSpeakNoDetection)
        assertTrue(first.isEmpty)

        val repeated = policy.onResult(CurrencyRecognizerPort.EMPTY_LABEL, confidence = 0.8f, hadDisplay = false, nowMs = 1_050L)
        assertFalse(repeated.shouldSpeakNoDetection)

        val later = policy.onResult(CurrencyRecognizerPort.EMPTY_LABEL, confidence = 0.8f, hadDisplay = false, nowMs = 1_100L)
        assertTrue(later.shouldSpeakNoDetection)

        val withDisplay = policy.onResult(CurrencyRecognizerPort.EMPTY_LABEL, confidence = 0.8f, hadDisplay = true, nowMs = 1_101L)
        assertTrue(withDisplay.shouldResetBuffer)
    }

    @Test
    fun resetAnnouncementDebounceAllowsSameDetectedLabelAgain() {
        val policy = CurrencyRecognitionPolicy(noDetectionRepeatMs = 100L)

        assertTrue(policy.onResult("50000 VND", 0.8f, hadDisplay = false, nowMs = 1_000L).shouldSpeakDetected)
        assertFalse(policy.onResult("50000 VND", 0.8f, hadDisplay = false, nowMs = 1_001L).shouldSpeakDetected)

        policy.resetAnnouncementDebounce()

        assertTrue(policy.onResult("50000 VND", 0.8f, hadDisplay = false, nowMs = 1_002L).shouldSpeakDetected)
    }
}
