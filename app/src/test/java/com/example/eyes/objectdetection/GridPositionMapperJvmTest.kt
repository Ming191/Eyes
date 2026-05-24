package com.example.eyes.objectdetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GridPositionMapperJvmTest {

    private val mapper = GridPositionMapper()

    @Test
    fun map_centerInEachCell_returnsExpectedPosition() {
        // GIVEN
        val frameWidth = 900
        val frameHeight = 600

        // WHEN / THEN
        assertEquals(DetectionPosition.TOP_LEFT, mapper.mapCenter(150f, 100f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.TOP_CENTER, mapper.mapCenter(450f, 100f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.TOP_RIGHT, mapper.mapCenter(750f, 100f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.CENTER_LEFT, mapper.mapCenter(150f, 300f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.CENTER, mapper.mapCenter(450f, 300f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.CENTER_RIGHT, mapper.mapCenter(750f, 300f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.BOTTOM_LEFT, mapper.mapCenter(150f, 500f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.BOTTOM_CENTER, mapper.mapCenter(450f, 500f, frameWidth, frameHeight))
        assertEquals(DetectionPosition.BOTTOM_RIGHT, mapper.mapCenter(750f, 500f, frameWidth, frameHeight))
    }

    @Test
    fun map_centerOnBoundary_usesNextCell() {
        // GIVEN
        val frameWidth = 900
        val frameHeight = 600

        // WHEN
        val firstBoundary = mapper.mapCenter(300f, 200f, frameWidth, frameHeight)
        val secondBoundary = mapper.mapCenter(600f, 400f, frameWidth, frameHeight)

        // THEN
        assertEquals(DetectionPosition.CENTER, firstBoundary)
        assertEquals(DetectionPosition.BOTTOM_RIGHT, secondBoundary)
    }

    @Test
    fun map_centerOutsideFrame_clampsToFrame() {
        // GIVEN
        val frameWidth = 900
        val frameHeight = 600

        // WHEN
        val topLeft = mapper.mapCenter(-50f, -50f, frameWidth, frameHeight)
        val bottomRight = mapper.mapCenter(950f, 650f, frameWidth, frameHeight)

        // THEN
        assertEquals(DetectionPosition.TOP_LEFT, topLeft)
        assertEquals(DetectionPosition.BOTTOM_RIGHT, bottomRight)
    }

    @Test
    fun map_invalidFrame_throws() {
        // GIVEN
        val centerX = 10f
        val centerY = 10f

        // WHEN / THEN
        assertThrows(IllegalArgumentException::class.java) {
            mapper.mapCenter(centerX, centerY, frameWidth = 0, frameHeight = 600)
        }
        assertThrows(IllegalArgumentException::class.java) {
            mapper.mapCenter(centerX, centerY, frameWidth = 900, frameHeight = 0)
        }
    }
}
