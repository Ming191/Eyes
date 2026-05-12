package com.example.eyes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DepthHazardDetectorTest {

    @Test
    fun detect_defaultDetectorEmitsStrongHazardOnFirstFreshDepthMap() {
        // GIVEN
        val detector = DepthHazardDetector()
        val map = depthMapWithHotRegion(zone = Zone.LEFT, band = VerticalBand.TORSO, hotValue = 0.92f)

        // WHEN
        val result = detector.detect(map)

        // THEN
        assertNotNull(result)
        assertEquals(Zone.LEFT, result!!.zone)
        assertEquals(VerticalBand.TORSO, result.band)
        assertEquals(HazardSeverity.HIGH, result.severity)
    }

    @Test
    fun detect_requiresPersistenceBeforeEmittingHazard() {
        // GIVEN
        val detector = DepthHazardDetector(persistenceFrames = 2)
        val map = depthMapWithHotRegion(zone = Zone.CENTER, band = VerticalBand.GROUND, hotValue = 0.92f)

        // WHEN
        val first = detector.detect(map)
        val second = detector.detect(map)

        // THEN
        assertNull(first)
        assertNotNull(second)
        assertEquals(Zone.CENTER, second!!.zone)
        assertEquals(VerticalBand.GROUND, second.band)
        assertEquals(HazardSeverity.HIGH, second.severity)
    }

    @Test
    fun detect_ignoresHeadBandEvenWhenHot() {
        // GIVEN
        val detector = DepthHazardDetector(persistenceFrames = 1)
        val map = depthMapWithHotRegion(zone = Zone.CENTER, band = VerticalBand.HEAD, hotValue = 0.95f)

        // WHEN
        val result = detector.detect(map)

        // THEN
        assertNull(result)
    }

    @Test
    fun detect_doesNotTriggerOnSmallNoisyPatch() {
        // GIVEN
        val detector = DepthHazardDetector(persistenceFrames = 1)
        val width = 9
        val height = 9
        val values = FloatArray(width * height) { 0.2f }
        // Tiny noisy patch in CENTER/GROUND (1/9 area)
        values[6 * width + 4] = 0.96f
        val map = DepthMap(values, width, height)

        // WHEN
        val result = detector.detect(map)

        // THEN
        assertNull(result)
    }

    @Test
    fun detect_emitsSideHazardWhenPeripheralRegionIsPartiallyHot() {
        // GIVEN
        val detector = DepthHazardDetector(persistenceFrames = 1)
        val width = 9
        val height = 9
        val values = FloatArray(width * height) { 0.2f }
        values[3 * width + 6] = 0.93f
        values[4 * width + 7] = 0.93f
        values[5 * width + 8] = 0.93f
        val map = DepthMap(values, width, height)

        // WHEN
        val result = detector.detect(map)

        // THEN
        assertNotNull(result)
        assertEquals(Zone.RIGHT, result!!.zone)
        assertEquals(VerticalBand.TORSO, result.band)
        assertEquals(HazardSeverity.HIGH, result.severity)
    }

    private fun depthMapWithHotRegion(zone: Zone, band: VerticalBand, hotValue: Float): DepthMap {
        val width = 9
        val height = 9
        val values = FloatArray(width * height) { 0.2f }

        val xRange = when (zone) {
            Zone.LEFT -> 0..2
            Zone.CENTER -> 3..5
            Zone.RIGHT -> 6..8
        }
        val yRange = when (band) {
            VerticalBand.HEAD -> 0..2
            VerticalBand.TORSO -> 3..5
            VerticalBand.GROUND -> 6..8
        }

        for (y in yRange) {
            for (x in xRange) {
                values[y * width + x] = hotValue
            }
        }
        return DepthMap(values, width, height)
    }
}
