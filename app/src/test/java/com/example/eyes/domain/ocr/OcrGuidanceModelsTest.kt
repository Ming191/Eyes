package com.example.eyes.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrGuidanceModelsTest {
    @Test
    fun boundsDerivedValuesAndUnionWork() {
        val first = OcrTextBounds(0.1f, 0.2f, 0.4f, 0.6f)
        val second = OcrTextBounds(0.0f, 0.3f, 0.8f, 0.9f)

        assertEquals(0.3f, first.width, 0.0001f)
        assertEquals(0.4f, first.height, 0.0001f)
        assertEquals(0.12f, first.area, 0.0001f)
        assertEquals(0.25f, first.centerX, 0.0001f)
        assertEquals(0.4f, first.centerY, 0.0001f)
        assertEquals(OcrTextBounds(0.0f, 0.2f, 0.8f, 0.9f), first.union(second))
    }

    @Test
    fun nestedGuidanceTextStoresAllMessages() {
        val text = OcrGuidanceEvaluator.OcrGuidanceText(
            searching = "searching",
            tooDark = "tooDark",
            tooBright = "tooBright",
            moveCloser = "moveCloser",
            moveBack = "moveBack",
            textClipped = "textClipped",
            moveLeft = "moveLeft",
            moveRight = "moveRight",
            moveUp = "moveUp",
            moveDown = "moveDown",
            ready = "ready",
            holdSteady = "holdSteady"
        )

        assertEquals("searching", text.searching)
        assertEquals("tooDark", text.tooDark)
        assertEquals("tooBright", text.tooBright)
        assertEquals("moveCloser", text.moveCloser)
        assertEquals("moveBack", text.moveBack)
        assertEquals("textClipped", text.textClipped)
        assertEquals("moveLeft", text.moveLeft)
        assertEquals("moveRight", text.moveRight)
        assertEquals("moveUp", text.moveUp)
        assertEquals("moveDown", text.moveDown)
        assertEquals("ready", text.ready)
        assertEquals("holdSteady", text.holdSteady)
        assertEquals("", OcrGuidanceEvaluator.OcrGuidanceText.EMPTY.ready)
    }
}
