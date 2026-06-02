package com.example.eyes.infrastructure.objectdetection

import com.example.eyes.domain.objectdetection.DetectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloPostprocessorTest {
    @Test
    fun postprocess_filtersByConfidenceSortsAndMapsPosition() {
        val output = output(candidates = 3, labels = 2)
        putCandidate(output, 3, 2, 0, x = 50f, y = 50f, w = 20f, h = 20f, scores = floatArrayOf(0.70f, 0.10f))
        putCandidate(output, 3, 2, 1, x = 150f, y = 150f, w = 30f, h = 30f, scores = floatArrayOf(0.20f, 0.95f))
        putCandidate(output, 3, 2, 2, x = 250f, y = 250f, w = 30f, h = 30f, scores = floatArrayOf(0.59f, 0.10f))
        val processor = YoloPostprocessor(inputSize = 300, labels = listOf("person", "dog"), confidenceThreshold = 0.60f)

        val result = processor.postprocess(output, frameWidth = 300, frameHeight = 300)

        assertEquals(2, result.size)
        assertEquals("dog", result[0].label)
        assertEquals(0.95f, result[0].confidence, 0.0001f)
        assertEquals(DetectionPosition.CENTER, result[0].position)
        assertEquals("person", result[1].label)
        assertEquals(DetectionPosition.TOP_LEFT, result[1].position)
    }

    @Test
    fun postprocess_suppressesOverlappingSameClassAndKeepsDifferentClass() {
        val output = output(candidates = 3, labels = 2)
        putCandidate(output, 3, 2, 0, x = 100f, y = 100f, w = 80f, h = 80f, scores = floatArrayOf(0.90f, 0.10f))
        putCandidate(output, 3, 2, 1, x = 105f, y = 105f, w = 80f, h = 80f, scores = floatArrayOf(0.80f, 0.10f))
        putCandidate(output, 3, 2, 2, x = 105f, y = 105f, w = 80f, h = 80f, scores = floatArrayOf(0.10f, 0.70f))
        val processor = YoloPostprocessor(inputSize = 300, labels = listOf("person", "dog"), confidenceThreshold = 0.60f)

        val result = processor.postprocess(output, frameWidth = 300, frameHeight = 300)

        assertEquals(2, result.size)
        assertEquals(listOf("person", "dog"), result.map { it.label })
    }

    @Test
    fun postprocess_dropsDegenerateBoxesAndCapsResults() {
        val output = output(candidates = 10, labels = 1)
        repeat(10) { index ->
            putCandidate(output, 10, 1, index, x = 10f + index * 25f, y = 20f, w = if (index == 0) 1f else 10f, h = 10f, scores = floatArrayOf(0.90f - index * 0.01f))
        }
        val processor = YoloPostprocessor(inputSize = 300, labels = listOf("person"), confidenceThreshold = 0.10f, maxDetections = 3)

        val result = processor.postprocess(output, frameWidth = 300, frameHeight = 300)

        assertEquals(3, result.size)
        assertTrue(result.none { it.boundingBox.width <= 1f })
    }

    private fun output(candidates: Int, labels: Int) = FloatArray((4 + labels) * candidates)

    private fun putCandidate(output: FloatArray, candidates: Int, labels: Int, candidate: Int, x: Float, y: Float, w: Float, h: Float, scores: FloatArray) {
        output[candidate] = x
        output[candidates + candidate] = y
        output[candidates * 2 + candidate] = w
        output[candidates * 3 + candidate] = h
        repeat(labels) { classId -> output[(4 + classId) * candidates + candidate] = scores[classId] }
    }
}
