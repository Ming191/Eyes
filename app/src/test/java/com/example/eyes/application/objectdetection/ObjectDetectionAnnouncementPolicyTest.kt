package com.example.eyes.application.objectdetection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectDetectionAnnouncementPolicyTest {
    @Test
    fun shouldSpeakReturnsFalseWhenNoObjects() {
        val policy = ObjectDetectionAnnouncementPolicy(announcementIntervalMs = 100L, announcementRepeatMs = 200L)

        assertFalse(policy.shouldSpeak("person", hasObjects = false, nowMs = 1_000L))
    }

    @Test
    fun shouldSpeakDebouncesRepeatedAndChangedAnnouncements() {
        val policy = ObjectDetectionAnnouncementPolicy(announcementIntervalMs = 100L, announcementRepeatMs = 200L)

        assertTrue(policy.shouldSpeak("person", hasObjects = true, nowMs = 1_000L))
        assertFalse(policy.shouldSpeak("person", hasObjects = true, nowMs = 1_100L))
        assertFalse(policy.shouldSpeak("chair", hasObjects = true, nowMs = 1_050L))
        assertTrue(policy.shouldSpeak("chair", hasObjects = true, nowMs = 1_101L))
        assertTrue(policy.shouldSpeak("chair", hasObjects = true, nowMs = 1_301L))
    }

    @Test
    fun resetClearsAnnouncementHistory() {
        val policy = ObjectDetectionAnnouncementPolicy(announcementIntervalMs = 100L, announcementRepeatMs = 200L)

        assertTrue(policy.shouldSpeak("person", hasObjects = true, nowMs = 1_000L))
        policy.reset()

        assertTrue(policy.shouldSpeak("person", hasObjects = true, nowMs = 1_001L))
    }
}
