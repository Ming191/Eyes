package com.example.eyes.domain.ocr

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class OcrGuidanceTracker(
    private val stableCenterDelta: Float = STABLE_CENTER_DELTA,
    private val stableAreaDelta: Float = STABLE_AREA_DELTA,
    private val speechIntervalMs: Long = SPEECH_INTERVAL_MS
) {
    private val lastBounds = AtomicReference<OcrTextBounds?>(null)
    private val stableFrames = AtomicInteger(0)
    private val lastAnnouncedStatus = AtomicReference<OcrGuidanceStatus?>(null)
    private val lastSpeechAtMs = AtomicLong(0L)

    fun updateStability(bounds: OcrTextBounds?): Int {
        if (bounds == null) {
            lastBounds.set(null)
            stableFrames.set(0)
            return 0
        }

        val previous = lastBounds.get()
        val stable = previous != null && bounds.isStableComparedTo(previous)
        val nextCount = if (stable) stableFrames.incrementAndGet() else 1
        stableFrames.set(nextCount)
        lastBounds.set(bounds)
        return nextCount
    }

    fun shouldAnnounce(status: OcrGuidanceStatus, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (status != OcrGuidanceStatus.READY) {
            lastAnnouncedStatus.set(null)
            return false
        }

        val previousStatus = lastAnnouncedStatus.get()
        val elapsed = nowMs - lastSpeechAtMs.get()
        if (previousStatus == status && elapsed < speechIntervalMs) return false

        lastAnnouncedStatus.set(status)
        lastSpeechAtMs.set(nowMs)
        return previousStatus != OcrGuidanceStatus.READY
    }

    fun reset() {
        lastBounds.set(null)
        stableFrames.set(0)
        lastAnnouncedStatus.set(null)
        lastSpeechAtMs.set(0L)
    }

    private fun OcrTextBounds.isStableComparedTo(other: OcrTextBounds): Boolean {
        val centerDelta = abs(centerX - other.centerX) + abs(centerY - other.centerY)
        val areaDelta = abs(area - other.area)
        return centerDelta < stableCenterDelta && areaDelta < stableAreaDelta
    }

    private companion object {
        private const val SPEECH_INTERVAL_MS = 4_000L
        private const val STABLE_CENTER_DELTA = 0.12f
        private const val STABLE_AREA_DELTA = 0.15f
    }
}
