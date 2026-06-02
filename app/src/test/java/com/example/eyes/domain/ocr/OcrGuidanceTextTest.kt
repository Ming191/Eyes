package com.example.eyes.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrGuidanceTextTest {
    @Test
    fun defaultsAreEmptyAndCopyChangesOnlyRequestedField() {
        val text = OcrGuidanceText().copy(ready = "ready")

        assertEquals("", text.searching)
        assertEquals("", text.tooDark)
        assertEquals("", text.tooBright)
        assertEquals("", text.moveCloser)
        assertEquals("", text.moveBack)
        assertEquals("", text.textClipped)
        assertEquals("", text.moveLeft)
        assertEquals("", text.moveRight)
        assertEquals("", text.moveUp)
        assertEquals("", text.moveDown)
        assertEquals("ready", text.ready)
        assertEquals("", text.holdSteady)
    }
}
