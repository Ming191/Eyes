package com.example.eyes.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGuidanceEvaluatorTest {

    @Test
    fun evaluate_noText_returnsSearching() {
        val result = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = null),
            stableFrameCount = 0
        )

        assertEquals(OcrGuidanceStatus.SEARCHING, result.status)
        assertFalse(result.isReadyToCapture)
    }

    @Test
    fun evaluate_smallText_requestsMoveCloser() {
        val result = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = OcrTextBounds(0.45f, 0.45f, 0.55f, 0.55f)),
            stableFrameCount = 1
        )

        assertEquals(OcrGuidanceStatus.MOVE_CLOSER, result.status)
    }

    @Test
    fun evaluate_largeText_requestsMoveBack() {
        val result = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = OcrTextBounds(0f, 0f, 1f, 1f)),
            stableFrameCount = 1
        )

        assertEquals(OcrGuidanceStatus.MOVE_BACK, result.status)
    }

    @Test
    fun evaluate_textNearEdge_warnsClipped() {
        val result = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = OcrTextBounds(0.005f, 0.18f, 0.55f, 0.52f)),
            stableFrameCount = 1
        )

        assertEquals(OcrGuidanceStatus.TEXT_CLIPPED, result.status)
    }

    @Test
    fun evaluate_textOffset_returnsDirectionGuidance() {
        val left = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = OcrTextBounds(0.08f, 0.30f, 0.42f, 0.68f)),
            stableFrameCount = 1
        )
        val right = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = OcrTextBounds(0.58f, 0.30f, 0.92f, 0.68f)),
            stableFrameCount = 1
        )
        val up = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = OcrTextBounds(0.30f, 0.08f, 0.70f, 0.42f)),
            stableFrameCount = 1
        )
        val down = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = OcrTextBounds(0.30f, 0.58f, 0.70f, 0.92f)),
            stableFrameCount = 1
        )

        assertEquals(OcrGuidanceStatus.MOVE_LEFT, left.status)
        assertEquals(OcrGuidanceStatus.MOVE_RIGHT, right.status)
        assertEquals(OcrGuidanceStatus.MOVE_UP, up.status)
        assertEquals(OcrGuidanceStatus.MOVE_DOWN, down.status)
    }

    @Test
    fun evaluate_badLight_warnsBeforeReady() {
        val dark = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = goodBounds(), luminance = 0.08f),
            stableFrameCount = 2
        )
        val bright = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = goodBounds(), luminance = 0.98f),
            stableFrameCount = 2
        )

        assertEquals(OcrGuidanceStatus.TOO_DARK, dark.status)
        assertEquals(OcrGuidanceStatus.TOO_BRIGHT, bright.status)
    }

    @Test
    fun evaluate_goodButNotStable_requestsHoldSteady() {
        val result = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = goodBounds()),
            stableFrameCount = 1
        )

        assertEquals(OcrGuidanceStatus.HOLD_STEADY, result.status)
        assertFalse(result.isReadyToCapture)
    }

    @Test
    fun evaluate_goodAndStable_returnsReady() {
        val result = OcrGuidanceEvaluator.evaluate(
            frame = frame(bounds = goodBounds()),
            stableFrameCount = 2
        )

        assertEquals(OcrGuidanceStatus.READY, result.status)
        assertTrue(result.isReadyToCapture)
    }

    private fun frame(
        bounds: OcrTextBounds?,
        luminance: Float = 0.55f
    ): OcrGuidanceFrame = OcrGuidanceFrame(
        textBounds = bounds,
        lineCount = if (bounds == null) 0 else 3,
        textLength = if (bounds == null) 0 else 80,
        luminance = luminance
    )

    private fun goodBounds(): OcrTextBounds = OcrTextBounds(0.18f, 0.24f, 0.82f, 0.72f)
}
