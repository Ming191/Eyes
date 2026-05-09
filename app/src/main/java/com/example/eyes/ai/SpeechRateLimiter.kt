package com.example.eyes.ai

class SpeechRateLimiter(
    private val cooldownMs: Long = 1_300L
) {
    private var lastSpokenAtMs: Long? = null

    fun shouldSpeak(nowMs: Long): Boolean {
        val last = lastSpokenAtMs ?: return true
        return nowMs - last >= cooldownMs
    }

    fun record(nowMs: Long) {
        lastSpokenAtMs = nowMs
    }
}
