package com.example.eyes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardAlertPipelineTest {

    @Test
    fun process_selectsHighestCompositeYoloCandidate() {
        // GIVEN
        val haptics = mutableListOf<Zone>()
        val speech = mutableListOf<String>()
        val pipeline = pipeline(
            haptics = haptics,
            speech = speech
        )
        val lowerConfidenceNear = detection(
            labelVi = "ghế",
            confidence = 0.85f,
            zone = Zone.LEFT,
            bboxDepthScore = 0.61f
        )
        val higherDepthNear = detection(
            labelVi = "người",
            confidence = 0.82f,
            zone = Zone.CENTER,
            bboxDepthScore = 0.95f
        )

        // WHEN
        val result = pipeline.process(
            detections = listOf(lowerConfidenceNear, higherDepthNear),
            alertSensitivity = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY,
            nowMs = 1_000L
        )

        // THEN
        assertEquals("Phát hiện người chính giữa", result.statusMessage)
        assertEquals("Chú ý! người ở chính giữa.", result.lastAnnouncement)
        assertTrue(result.speechSpoken)
        assertEquals(listOf(Zone.CENTER), haptics)
        assertEquals(listOf("Chú ý! người ở chính giữa."), speech)
    }

    @Test
    fun process_usesFreshDepthCandidateWithReliableLabel() {
        // GIVEN
        val haptics = mutableListOf<Zone>()
        val speech = mutableListOf<String>()
        val pipeline = pipeline(
            depthHazard = DepthHazard(
                zone = Zone.RIGHT,
                band = VerticalBand.GROUND,
                severity = HazardSeverity.HIGH,
                score = 0.92f
            ),
            depthHazardAtMs = 900L,
            haptics = haptics,
            speech = speech
        )

        // WHEN
        val result = pipeline.process(
            detections = listOf(
                detection(
                    labelVi = "thùng",
                    confidence = 0.91f,
                    zone = Zone.RIGHT,
                    bboxDepthScore = 0.1f
                )
            ),
            alertSensitivity = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY,
            nowMs = 1_000L
        )

        // THEN
        assertEquals("Phát hiện thùng gần bên phải", result.statusMessage)
        assertEquals("Chú ý! thùng gần bên phải.", result.lastAnnouncement)
        assertEquals(listOf(Zone.RIGHT), haptics)
        assertEquals(listOf("Chú ý! thùng gần bên phải."), speech)
    }

    @Test
    fun process_ignoresStaleDepthCandidate() {
        // GIVEN
        val pipeline = pipeline(
            depthHazard = DepthHazard(
                zone = Zone.LEFT,
                band = VerticalBand.GROUND,
                severity = HazardSeverity.HIGH,
                score = 0.95f
            ),
            depthHazardAtMs = 1_000L
        )

        // WHEN
        val result = pipeline.process(
            detections = emptyList(),
            alertSensitivity = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY,
            nowMs = 1_000L + HazardAlertPipeline.DEPTH_HAZARD_TTL_MS + 1L
        )

        // THEN
        assertNull(result.statusMessage)
        assertNull(result.lastAnnouncement)
        assertFalse(result.speechSpoken)
    }

    @Test
    fun process_rateLimitsHapticAndSpeech() {
        val haptics = mutableListOf<Zone>()
        val speech = mutableListOf<String>()
        val pipeline = pipeline(
            haptics = haptics,
            speech = speech
        )
        val candidate = detection(
            labelVi = "xe máy",
            confidence = 0.88f,
            zone = Zone.LEFT,
            bboxDepthScore = 0.9f
        )

        // GIVEN
        pipeline.process(
            detections = listOf(candidate),
            alertSensitivity = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY,
            nowMs = 1_000L
        )

        // WHEN
        val result = pipeline.process(
            detections = listOf(candidate),
            alertSensitivity = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY,
            nowMs = 1_100L
        )

        // THEN
        assertFalse(result.speechSpoken)
        assertEquals(listOf(Zone.LEFT), haptics)
        assertEquals(listOf("Chú ý! xe máy ở bên trái."), speech)
    }

    @Test
    fun process_reportsSafeStatusAfterStreak() {
        val pipeline = pipeline()

        // GIVEN
        val firstResult = pipeline.process(
            detections = emptyList(),
            alertSensitivity = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY,
            nowMs = 1_000L
        )

        // WHEN
        val secondResult = pipeline.process(
            detections = emptyList(),
            alertSensitivity = HazardAlertPipeline.DEFAULT_ALERT_SENSITIVITY,
            nowMs = 1_100L
        )

        // THEN
        assertNull(firstResult.statusMessage)
        assertEquals("Lối đi tạm ổn, tiếp tục quét môi trường", secondResult.statusMessage)
    }

    private fun pipeline(
        depthHazard: DepthHazard? = null,
        depthHazardAtMs: Long = 0L,
        headsetConnected: Boolean = false,
        haptics: MutableList<Zone> = mutableListOf(),
        speech: MutableList<String> = mutableListOf()
    ): HazardAlertPipeline {
        return HazardAlertPipeline(
            hazardFusionEngine = HazardFusionEngine(),
            latestDepthHazard = { depthHazard },
            latestDepthHazardAtMs = { depthHazardAtMs },
            isHeadsetConnected = { headsetConnected },
            dispatchHaptic = { zone -> haptics += zone },
            speakUrgent = { announcement -> speech += announcement }
        )
    }

    private fun detection(
        labelVi: String,
        confidence: Float,
        zone: Zone,
        bboxDepthScore: Float,
        labelEn: String = "person"
    ): Detection {
        return Detection(
            labelEn = labelEn,
            labelVi = labelVi,
            bbox = BBox(left = 0f, top = 0f, right = 1f, bottom = 1f),
            confidence = confidence,
            zone = zone,
            bboxDepthScore = bboxDepthScore
        )
    }
}
