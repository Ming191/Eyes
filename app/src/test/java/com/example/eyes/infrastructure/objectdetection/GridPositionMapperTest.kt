package com.example.eyes.infrastructure.objectdetection

import com.example.eyes.domain.objectdetection.DetectionBounds
import com.example.eyes.domain.objectdetection.DetectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GridPositionMapperTest {
    private val mapper = GridPositionMapper()

    @Test
    fun mapCenter_returnsExpectedGridCells() {
        assertEquals(DetectionPosition.TOP_LEFT, mapper.mapCenter(10f, 10f, 300, 300))
        assertEquals(DetectionPosition.TOP_CENTER, mapper.mapCenter(150f, 10f, 300, 300))
        assertEquals(DetectionPosition.TOP_RIGHT, mapper.mapCenter(250f, 10f, 300, 300))
        assertEquals(DetectionPosition.CENTER_LEFT, mapper.mapCenter(10f, 150f, 300, 300))
        assertEquals(DetectionPosition.CENTER, mapper.mapCenter(150f, 150f, 300, 300))
        assertEquals(DetectionPosition.CENTER_RIGHT, mapper.mapCenter(250f, 150f, 300, 300))
        assertEquals(DetectionPosition.BOTTOM_LEFT, mapper.mapCenter(10f, 250f, 300, 300))
        assertEquals(DetectionPosition.BOTTOM_CENTER, mapper.mapCenter(150f, 250f, 300, 300))
        assertEquals(DetectionPosition.BOTTOM_RIGHT, mapper.mapCenter(250f, 250f, 300, 300))
    }

    @Test
    fun map_clampsOutOfFrameCenters() {
        val bounds = DetectionBounds(left = -100f, top = 400f, right = -50f, bottom = 450f)

        val result = mapper.map(bounds, frameWidth = 300, frameHeight = 300)

        assertEquals(DetectionPosition.BOTTOM_LEFT, result)
    }

    @Test
    fun mapCenter_rejectsInvalidFrameSize() {
        assertThrows(IllegalArgumentException::class.java) { mapper.mapCenter(0f, 0f, 0, 300) }
        assertThrows(IllegalArgumentException::class.java) { mapper.mapCenter(0f, 0f, 300, 0) }
    }
}
