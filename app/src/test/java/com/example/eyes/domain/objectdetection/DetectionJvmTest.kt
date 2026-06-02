package com.example.eyes.domain.objectdetection

import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionJvmTest {

    @Test
    fun bounds_positiveDimensions_returnsGeometry() {
        // GIVEN
        val bounds = DetectionBounds(left = 10f, top = 20f, right = 50f, bottom = 80f)

        // THEN
        assertEquals(40f, bounds.width, 0f)
        assertEquals(60f, bounds.height, 0f)
        assertEquals(30f, bounds.centerX, 0f)
        assertEquals(50f, bounds.centerY, 0f)
    }

    @Test
    fun bounds_invertedEdges_clampsDimensionsButKeepsCenter() {
        // GIVEN
        val bounds = DetectionBounds(left = 80f, top = 90f, right = 20f, bottom = 10f)

        // THEN
        assertEquals(0f, bounds.width, 0f)
        assertEquals(0f, bounds.height, 0f)
        assertEquals(50f, bounds.centerX, 0f)
        assertEquals(50f, bounds.centerY, 0f)
    }

    @Test
    fun detection_storesAllProperties() {
        // GIVEN
        val bounds = DetectionBounds(left = 1f, top = 2f, right = 3f, bottom = 4f)

        // WHEN
        val detection = Detection(
            classId = 7,
            label = "chair",
            confidence = 0.75f,
            boundingBox = bounds,
            position = DetectionPosition.CENTER_RIGHT,
        )

        // THEN
        assertEquals(7, detection.classId)
        assertEquals("chair", detection.label)
        assertEquals(0.75f, detection.confidence, 0f)
        assertEquals(bounds, detection.boundingBox)
        assertEquals(DetectionPosition.CENTER_RIGHT, detection.position)
    }

    @Test
    fun yoloOutputInfo_storesAllProperties() {
        // WHEN
        val outputInfo = YoloOutputInfo(
            index = 1,
            shape = listOf(1L, 84L, 8400L),
            dtype = "float32",
            elementCount = 705_600L,
        )

        // THEN
        assertEquals(1, outputInfo.index)
        assertEquals(listOf(1L, 84L, 8400L), outputInfo.shape)
        assertEquals("float32", outputInfo.dtype)
        assertEquals(705_600L, outputInfo.elementCount)
    }

    @Test
    fun detectionPosition_valuesRemainInExpectedOrder() {
        // WHEN
        val values = DetectionPosition.values().toList()

        // THEN
        assertEquals(
            listOf(
                DetectionPosition.TOP_LEFT,
                DetectionPosition.TOP_CENTER,
                DetectionPosition.TOP_RIGHT,
                DetectionPosition.CENTER_LEFT,
                DetectionPosition.CENTER,
                DetectionPosition.CENTER_RIGHT,
                DetectionPosition.BOTTOM_LEFT,
                DetectionPosition.BOTTOM_CENTER,
                DetectionPosition.BOTTOM_RIGHT,
            ),
            values,
        )
    }
}
