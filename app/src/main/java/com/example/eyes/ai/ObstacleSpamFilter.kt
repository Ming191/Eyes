package com.example.eyes.ai

class ObstacleSpamFilter(
    private val cooldownMs: Long = 3_000L,
    private val currentTimeMs: () -> Long = System::currentTimeMillis
) {
    private val lastAnnounced = HashMap<String, Long>()

    fun isSpam(detection: Detection): Boolean {
        val key = keyFor(detection)
        val lastTime = lastAnnounced[key] ?: return false
        return currentTimeMs() - lastTime < cooldownMs
    }

    fun record(detection: Detection) {
        lastAnnounced[keyFor(detection)] = currentTimeMs()
    }

    private fun keyFor(detection: Detection): String = "${detection.labelEn}_${detection.zone}"
}
