package com.example.eyes.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObstacleSpamFilterTest {

    @Before
    fun setUp() {
        runCatching { stopKoin() }
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun isSpam_blocksRepeatedAnnouncementsInsideCooldownWindow() {
        // GIVEN
        var now = 1_000L
        val filter = ObstacleSpamFilter(cooldownMs = 3_000L) { now }
        val detection = sampleDetection(zone = Zone.CENTER)

        // WHEN
        filter.record(detection)

        // THEN
        assertTrue(filter.isSpam(detection))

        // WHEN
        now = 4_500L

        // THEN
        assertFalse(filter.isSpam(detection))
    }

    @Test
    fun isSpam_tracksCooldownPerLabelAndZone() {
        // GIVEN
        var now = 2_000L
        val filter = ObstacleSpamFilter(cooldownMs = 3_000L) { now }
        val center = sampleDetection(zone = Zone.CENTER)
        val left = sampleDetection(zone = Zone.LEFT)

        // WHEN
        filter.record(center)

        // THEN
        assertTrue(filter.isSpam(center))
        assertFalse(filter.isSpam(left))
    }

    private fun sampleDetection(zone: Zone): Detection {
        return Detection(
            labelEn = "person",
            labelVi = "người",
            bbox = BBox(0.2f, 0.2f, 0.6f, 0.9f),
            confidence = 0.9f,
            zone = zone,
            bboxDepthScore = 0.8f,
            midasDepth = 0.7f
        )
    }
}
