package com.example.eyes.application.objectdetection

class ObjectDetectionAnnouncementPolicy(
    private val announcementIntervalMs: Long = ANNOUNCEMENT_INTERVAL_MS,
    private val announcementRepeatMs: Long = ANNOUNCEMENT_REPEAT_MS
) {
    private var lastAnnouncement: String = ""
    private var lastAnnouncementAtMs: Long = 0L

    fun shouldSpeak(
        announcement: String,
        hasObjects: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!hasObjects) return false
        val elapsed = nowMs - lastAnnouncementAtMs
        if (announcement == lastAnnouncement && elapsed < announcementRepeatMs) return false
        if (announcement != lastAnnouncement && elapsed < announcementIntervalMs) return false

        lastAnnouncement = announcement
        lastAnnouncementAtMs = nowMs
        return true
    }

    fun reset() {
        lastAnnouncement = ""
        lastAnnouncementAtMs = 0L
    }

    private companion object {
        private const val ANNOUNCEMENT_INTERVAL_MS = 3_000L
        private const val ANNOUNCEMENT_REPEAT_MS = 6_000L
    }
}
