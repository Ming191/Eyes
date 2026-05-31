package com.example.eyes.application.currency

import com.example.eyes.application.ports.CurrencyRecognizerPort

class CurrencyRecognitionPolicy(
    private val noDetectionRepeatMs: Long = NO_DETECTION_REPEAT_MS
) {
    private var lastAnnouncement: String = ""
    private var lastNoDetectionAtMs: Long = 0L

    fun onResult(
        label: String,
        confidence: Float,
        hadDisplay: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): CurrencyRecognitionDecision {
        val safeConfidence = confidence.coerceIn(0f, 1f)
        if (label == CurrencyRecognizerPort.EMPTY_LABEL) {
            val shouldSpeakNoDetection = nowMs - lastNoDetectionAtMs >= noDetectionRepeatMs
            if (shouldSpeakNoDetection) lastNoDetectionAtMs = nowMs
            if (hadDisplay) resetAnnouncementDebounce()
            return CurrencyRecognitionDecision(
                label = null,
                safeConfidence = 0f,
                shouldResetBuffer = hadDisplay,
                shouldSpeakDetected = false,
                shouldSpeakNoDetection = shouldSpeakNoDetection,
                isEmpty = true
            )
        }

        val shouldSpeakDetected = lastAnnouncement != label
        if (shouldSpeakDetected) lastAnnouncement = label
        return CurrencyRecognitionDecision(
            label = label,
            safeConfidence = safeConfidence,
            shouldResetBuffer = false,
            shouldSpeakDetected = shouldSpeakDetected,
            shouldSpeakNoDetection = false,
            isEmpty = false
        )
    }

    fun resetAnnouncementDebounce() {
        lastAnnouncement = ""
        lastNoDetectionAtMs = 0L
    }

    private companion object {
        private const val NO_DETECTION_REPEAT_MS = 10_000L
    }
}

data class CurrencyRecognitionDecision(
    val label: String?,
    val safeConfidence: Float,
    val shouldResetBuffer: Boolean,
    val shouldSpeakDetected: Boolean,
    val shouldSpeakNoDetection: Boolean,
    val isEmpty: Boolean
)
