package com.example.eyes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HazardFusionEngineTest {

    @Test
    fun fuse_sameZone_suppressesDepthDuplicate() {
        // GIVEN
        val engine = HazardFusionEngine()
        val yolo = sampleDetection(zone = Zone.LEFT)
        val depth = DepthHazard(zone = Zone.LEFT, band = VerticalBand.TORSO, severity = HazardSeverity.MEDIUM, score = 0.68f)

        // WHEN
        val alert = engine.fuse(yolo, depth)

        // THEN
        assertEquals(AlertSource.YOLO, alert!!.primarySource)
        assertEquals(Zone.LEFT, alert.primaryZone)
        assertNull(alert.secondaryHapticZone)
    }

    @Test
    fun fuse_crossZone_keepsYoloAndAddsSecondaryHaptic() {
        // GIVEN
        val engine = HazardFusionEngine()
        val yolo = sampleDetection(zone = Zone.CENTER)
        val depth = DepthHazard(zone = Zone.RIGHT, band = VerticalBand.GROUND, severity = HazardSeverity.HIGH, score = 0.84f)

        // WHEN
        val alert = engine.fuse(yolo, depth)

        // THEN
        assertEquals(AlertSource.YOLO, alert!!.primarySource)
        assertEquals(Zone.CENTER, alert.primaryZone)
        assertEquals(Zone.RIGHT, alert.secondaryHapticZone)
    }

    @Test
    fun fuse_depthOnlyMedium_returnsDepthPrimary() {
        // GIVEN
        val engine = HazardFusionEngine()
        val depth = DepthHazard(zone = Zone.CENTER, band = VerticalBand.GROUND, severity = HazardSeverity.MEDIUM, score = 0.83f)

        // WHEN
        val alert = engine.fuse(yoloDetection = null, depthHazard = depth)

        // THEN
        assertEquals(AlertSource.DEPTH, alert!!.primarySource)
        assertEquals(Zone.CENTER, alert.primaryZone)
    }

    @Test
    fun fuse_depthOnlyHigh_returnsDepthPrimary() {
        // GIVEN
        val engine = HazardFusionEngine()
        val depth = DepthHazard(zone = Zone.RIGHT, band = VerticalBand.GROUND, severity = HazardSeverity.HIGH, score = 0.91f)

        // WHEN
        val alert = engine.fuse(yoloDetection = null, depthHazard = depth)

        // THEN
        assertEquals(AlertSource.DEPTH, alert!!.primarySource)
        assertEquals(Zone.RIGHT, alert.primaryZone)
    }

    private fun sampleDetection(zone: Zone): Detection {
        return Detection(
            labelEn = "person",
            labelVi = "người",
            bbox = BBox(0f, 0f, 1f, 1f),
            confidence = 0.95f,
            zone = zone,
            bboxDepthScore = 0.7f
        )
    }
}
