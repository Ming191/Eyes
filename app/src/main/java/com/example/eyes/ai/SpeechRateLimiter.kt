package com.example.eyes.ai

class SpeechRateLimiter(
    private val cooldownMs: Long = 1_300L
) {
    private var lastSpokenAtMs: Long? = null

    /**
     * Determines whether speech should be allowed at the given time based on the configured cooldown.
     *
     * @param nowMs The current time in milliseconds.
     * @return `true` if there is no prior recorded speech time or the interval since the last speech is greater than or equal to the cooldown; `false` otherwise.
     */
    fun shouldSpeak(nowMs: Long): Boolean {
        val last = lastSpokenAtMs ?: return true
        return nowMs - last >= cooldownMs
    }

    /**
     * Records the provided timestamp as the most recent speech time.
     *
     * @param nowMs The current time in milliseconds to store as the last spoken timestamp.
     */
    fun record(nowMs: Long) {
        lastSpokenAtMs = nowMs
    }
}
