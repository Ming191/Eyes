package com.example.eyes.ai

import android.annotation.SuppressLint
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class AlertResult(
    val statusMessage: String?,
    val lastAnnouncement: String?,
    val debugMetrics: String,
    val speechSpoken: Boolean
)

class HazardAlertPipeline(
    private val hazardFusionEngine: HazardFusionEngine,
    private val speechRateLimiter: SpeechRateLimiter = SpeechRateLimiter(cooldownMs = SPEECH_COOLDOWN_MS),
    private val latestDepthHazard: () -> DepthHazard?,
    private val latestDepthHazardAtMs: () -> Long,
    private val isHeadsetConnected: () -> Boolean,
    private val dispatchHaptic: (Zone) -> Unit,
    private val speakUrgent: (String) -> Unit,
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() }
) {
    private val lastHapticAtMs = AtomicLong(0L)
    private val noHazardStreak = AtomicInteger(0)

    fun process(
        detections: List<Detection>,
        alertSensitivity: Float,
        nowMs: Long = currentTimeMs()
    ): AlertResult {
        val yoloCandidate = selectYoloCandidate(detections, alertSensitivity)
        val yoloCompositeScore = yoloCandidate?.compositeScore()
        val depthCandidate = getFreshDepthCandidate(nowMs)
        val depthLabelCandidate = depthCandidate?.let { findReliableLabelForDepth(detections, it.zone) }
        val fusedAlert = hazardFusionEngine.fuse(yoloCandidate, depthCandidate)
        val headsetConnected = isHeadsetConnected()

        if (fusedAlert == null) {
            val safeStreak = noHazardStreak.updateAndGet { current ->
                (current + 1).coerceAtMost(SAFE_STATUS_STREAK_FRAMES + 1)
            }
            return AlertResult(
                statusMessage = if (safeStreak >= SAFE_STATUS_STREAK_FRAMES) {
                    "Lối đi tạm ổn, tiếp tục quét môi trường"
                } else {
                    null
                },
                lastAnnouncement = null,
                debugMetrics = buildDebugMetrics(
                    yoloCandidate = yoloCandidate,
                    yoloCompositeScore = yoloCompositeScore,
                    depthCandidate = depthCandidate,
                    fusedAlert = null,
                    speechSpoken = false,
                    speechSuppressedByHeadset = headsetConnected,
                    sensitivity = alertSensitivity
                ),
                speechSpoken = false
            )
        }

        noHazardStreak.set(0)

        if (shouldTriggerHaptic(nowMs)) {
            dispatchHaptic(fusedAlert.primaryZone)
            fusedAlert.secondaryHapticZone?.let(dispatchHaptic)
        }

        val announcement = buildAnnouncement(fusedAlert, yoloCandidate, depthLabelCandidate)
        var speechSpoken = false
        if (!headsetConnected && speechRateLimiter.shouldSpeak(nowMs)) {
            speakUrgent(announcement)
            speechRateLimiter.record(nowMs)
            speechSpoken = true
        }

        return AlertResult(
            statusMessage = buildStatusMessage(fusedAlert, yoloCandidate, depthLabelCandidate),
            lastAnnouncement = announcement,
            debugMetrics = buildDebugMetrics(
                yoloCandidate = yoloCandidate,
                yoloCompositeScore = yoloCompositeScore,
                depthCandidate = depthCandidate,
                fusedAlert = fusedAlert,
                speechSpoken = speechSpoken,
                speechSuppressedByHeadset = headsetConnected,
                sensitivity = alertSensitivity
            ),
            speechSpoken = speechSpoken
        )
    }

    fun resetSafeStatus() {
        noHazardStreak.set(0)
    }

    private fun selectYoloCandidate(
        detections: List<Detection>,
        alertSensitivity: Float
    ): Detection? {
        return detections
            .asSequence()
            .filter { it.isAlertCandidate() }
            .filter { it.isNearby(alertSensitivity) }
            .maxByOrNull { it.compositeScore() }
    }

    private fun Detection.compositeScore(): Float {
        val depthScore = if (midasDepth > 0f) midasDepth else bboxDepthScore
        return (depthScore * 0.7f) + (confidence * 0.3f)
    }

    private fun getFreshDepthCandidate(nowMs: Long): DepthHazard? {
        val hazardAtMs = latestDepthHazardAtMs()
        if (hazardAtMs <= 0L) return null
        if (nowMs - hazardAtMs > DEPTH_HAZARD_TTL_MS) return null
        return latestDepthHazard()
    }

    private fun findReliableLabelForDepth(detections: List<Detection>, zone: Zone): Detection? {
        return detections
            .asSequence()
            .filter { it.zone == zone }
            .filter { it.hasReliableLabel() }
            .maxByOrNull { it.confidence }
    }

    private fun shouldTriggerHaptic(nowMs: Long): Boolean {
        while (true) {
            val lastTriggeredAtMs = lastHapticAtMs.get()
            if (nowMs - lastTriggeredAtMs < HAPTIC_COOLDOWN_MS) return false
            if (lastHapticAtMs.compareAndSet(lastTriggeredAtMs, nowMs)) return true
        }
    }

    private fun buildAnnouncement(
        fusedAlert: FusedHazardAlert,
        yoloCandidate: Detection?,
        depthLabelCandidate: Detection?
    ): String {
        return when {
            fusedAlert.primarySource == AlertSource.YOLO && yoloCandidate != null -> {
                "Chú ý! ${yoloCandidate.labelVi} ở ${yoloCandidate.zone.labelVi}."
            }
            fusedAlert.primarySource == AlertSource.DEPTH && depthLabelCandidate != null -> {
                "Chú ý! ${depthLabelCandidate.labelVi} gần ${fusedAlert.primaryZone.labelVi}."
            }
            else -> fusedAlert.speechText ?: "Chú ý! Có vật cản gần ${fusedAlert.primaryZone.labelVi}."
        }
    }

    private fun buildStatusMessage(
        fusedAlert: FusedHazardAlert,
        yoloCandidate: Detection?,
        depthLabelCandidate: Detection?
    ): String {
        return when (fusedAlert.primarySource) {
            AlertSource.YOLO -> {
                val label = yoloCandidate?.labelVi ?: "vật cản"
                "Phát hiện $label ${fusedAlert.primaryZone.labelVi}"
            }
            AlertSource.DEPTH -> {
                val label = depthLabelCandidate?.labelVi ?: "vật cản"
                "Phát hiện $label gần ${fusedAlert.primaryZone.labelVi}"
            }
        }
    }

    private fun buildDebugMetrics(
        yoloCandidate: Detection?,
        yoloCompositeScore: Float?,
        depthCandidate: DepthHazard?,
        fusedAlert: FusedHazardAlert?,
        speechSpoken: Boolean,
        speechSuppressedByHeadset: Boolean,
        sensitivity: Float
    ): String {
        val yoloLine = if (yoloCandidate == null) {
            "YOLO: không có candidate"
        } else {
            val depthScore = if (yoloCandidate.midasDepth > 0f) {
                yoloCandidate.midasDepth
            } else {
                yoloCandidate.bboxDepthScore
            }
            "YOLO: ${yoloCandidate.labelVi} ${yoloCandidate.zone.labelVi} | conf=${fmt(yoloCandidate.confidence)} depth=${fmt(depthScore)} score=${fmt(yoloCompositeScore ?: 0f)}"
        }

        val depthLine = if (depthCandidate == null) {
            "MiDaS: không có hazard đạt ngưỡng"
        } else {
            val severityLabel = when (depthCandidate.severity) {
                HazardSeverity.HIGH -> "HIGH"
                HazardSeverity.MEDIUM -> "MEDIUM"
            }
            "MiDaS: ${depthCandidate.zone.labelVi} ${depthCandidate.band.name} | severity=$severityLabel score=${fmt(depthCandidate.score)}"
        }

        val fusionLine = if (fusedAlert == null) {
            "Fusion: không cảnh báo"
        } else {
            val secondaryLabel = fusedAlert.secondaryHapticZone?.labelVi ?: "không"
            "Fusion: primary=${fusedAlert.primarySource.name} ${fusedAlert.primaryZone.labelVi} | secondary=$secondaryLabel"
        }

        val speechLine = "Speech: spoken=$speechSpoken headset=$speechSuppressedByHeadset"
        val configLine = "Cfg: sensitivity=${fmt(sensitivity)}"

        return listOf(yoloLine, depthLine, fusionLine, speechLine, configLine).joinToString("\n")
    }

    private fun fmt(value: Float): String = String.format(Locale.getDefault(), "%.2f", value)

    companion object {
        const val DEPTH_HAZARD_TTL_MS = 2_800L
        const val HAPTIC_COOLDOWN_MS = 300L
        const val SPEECH_COOLDOWN_MS = 1_300L
        const val DEFAULT_ALERT_SENSITIVITY = 0.5f

        private const val SAFE_STATUS_STREAK_FRAMES = 2
    }
}
