package com.example.eyes.camera

class FrameThrottle(
    private val intervalMs: Long = 66L
) {
    private var lastProcessedAtMs: Long? = null

    fun shouldProcess(currentTimeMs: Long): Boolean {
        val last = lastProcessedAtMs
        if (last == null || currentTimeMs - last >= intervalMs) {
            lastProcessedAtMs = currentTimeMs
            return true
        }
        return false
    }
}
