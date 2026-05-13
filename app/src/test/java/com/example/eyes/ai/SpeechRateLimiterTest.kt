package com.example.eyes.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRateLimiterTest {

    @Test
    fun shouldSpeak_respectsGlobalCooldown() {
        // GIVEN
        val limiter = SpeechRateLimiter(cooldownMs = 3_000L)

        // THEN
        assertTrue(limiter.shouldSpeak(1_000L))

        // WHEN
        limiter.record(1_000L)

        // THEN
        assertFalse(limiter.shouldSpeak(2_500L))
        assertTrue(limiter.shouldSpeak(4_000L))
    }
}
