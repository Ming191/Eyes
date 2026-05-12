package com.example.eyes.ai

class ObstacleSpamFilter(
    private val cooldownMs: Long = 3_000L,
    private val currentTimeMs: () -> Long = System::currentTimeMillis
) {
    private val lastAnnounced = HashMap<String, Long>()

    /**
     * Determines whether a detection should be treated as spam based on the configured cooldown.
     *
     * @param detection The detection to check; the lookup key is derived from `detection.labelEn` and `detection.zone`.
     * @return `true` if the detection was announced less than `cooldownMs` milliseconds ago, `false` otherwise.
     */
    fun isSpam(detection: Detection): Boolean {
        val key = keyFor(detection)
        val lastTime = lastAnnounced[key] ?: return false
        return currentTimeMs() - lastTime < cooldownMs
    }

    /**
     * Records the detection's announcement time to suppress immediate repeat announcements.
     *
     * @param detection The detection whose key (derived from its label and zone) will be updated to the current timestamp.
     */
    fun record(detection: Detection) {
        lastAnnounced[keyFor(detection)] = currentTimeMs()
    }

    /**
 * Creates a stable key that identifies a detection by its English label and zone.
 *
 * @param detection The detection to derive the key from.
 * @return A string in the form `"<labelEn>_<zone>"` combining `detection.labelEn` and `detection.zone`.
 */
private fun keyFor(detection: Detection): String = "${detection.labelEn}_${detection.zone}"
}
