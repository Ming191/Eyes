package com.example.eyes.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameThrottleTest {

    @Test
    fun shouldProcess_firstFrame_passes() {
        // GIVEN
        val throttle = FrameThrottle(intervalMs = 200)

        // WHEN
        val result = throttle.shouldProcess(currentTimeMs = 1_000)

        // THEN
        assertTrue(result)
    }

    @Test
    fun shouldProcess_secondFrameWithin200ms_drops() {
        // GIVEN
        val throttle = FrameThrottle(intervalMs = 200)
        throttle.shouldProcess(currentTimeMs = 1_000)

        // WHEN
        val result = throttle.shouldProcess(currentTimeMs = 1_150)

        // THEN
        assertFalse(result)
    }

    @Test
    fun shouldProcess_frameAtOrAfter200ms_passes() {
        // GIVEN
        val throttle = FrameThrottle(intervalMs = 200)
        throttle.shouldProcess(currentTimeMs = 1_000)

        // WHEN
        val atBoundary = throttle.shouldProcess(currentTimeMs = 1_200)
        val afterBoundary = throttle.shouldProcess(currentTimeMs = 1_400)

        // THEN
        assertTrue(atBoundary)
        assertTrue(afterBoundary)
    }
}
