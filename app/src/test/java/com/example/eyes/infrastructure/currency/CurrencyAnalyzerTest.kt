package com.example.eyes.infrastructure.currency

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CurrencyAnalyzerTest {
    @Test
    fun analyzeReturnsLabelWhenFakeRecognizerConfidenceHigh() {
        val results = mutableListOf<Pair<String, Float>>()
        val analyzer = CurrencyAnalyzer(FakeRecognizer(classification = "50000" to 0.91f)) { label, confidence ->
            results += label to confidence
        }

        analyzer.analyze(Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) })

        assertEquals(listOf("50000" to 0.91f), results)
    }

    @Test
    fun analyzeReturnsEmptyWhenDetectionMissingOrConfidenceLow() {
        val results = mutableListOf<Pair<String, Float>>()
        val noDetection = CurrencyAnalyzer(FakeRecognizer(box = null)) { label, confidence -> results += label to confidence }
        val lowConfidence = CurrencyAnalyzer(FakeRecognizer(classification = "10000" to 0.69f)) { label, confidence ->
            results += label to confidence
        }

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        noDetection.analyze(bitmap)
        lowConfidence.analyze(bitmap)

        assertEquals(listOf("" to 0f, "" to 0f), results)
    }

    @Test
    fun resetBufferAndCloseDelegateToRecognizer() {
        val fake = FakeRecognizer()
        val analyzer = CurrencyAnalyzer(fake) { _, _ -> }

        analyzer.resetBuffer()
        analyzer.close()

        assertTrue(fake.closed)
    }

    @Test
    fun streamingFrameRequiresStableHighConfidenceWindow() {
        val results = mutableListOf<Pair<String, Float>>()
        val analyzer = CurrencyAnalyzer(FakeRecognizer(classification = "20000" to 0.9f)) { label, confidence ->
            results += label to confidence
        }
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        repeat(3) { analyzer.analyzeStreamingFrame(bitmap) }

        assertEquals("", results[0].first)
        assertEquals("", results[1].first)
        assertEquals("20000", results[2].first)
        assertEquals(0.9f, results[2].second, 0.0001f)
    }

    @Test
    fun streamingFrameHandlesMissingDetectionAndInvalidCrop() {
        val results = mutableListOf<Pair<String, Float>>()
        val missing = CurrencyAnalyzer(FakeRecognizer(box = null)) { label, confidence -> results += label to confidence }
        val invalidCrop = CurrencyAnalyzer(FakeRecognizer(box = CurrencyAnalyzer.BBox(0.5f, 0.5f, 0.5f, 1f))) { label, confidence ->
            results += label to confidence
        }
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        repeat(3) { missing.analyzeStreamingFrame(bitmap) }
        invalidCrop.analyzeStreamingFrame(bitmap)

        assertEquals(listOf("" to 0f, "" to 0f, "" to 0f, "" to 0f), results)
    }

    @Test
    fun analyzeReturnsEmptyWhenRecognizerThrows() {
        val results = mutableListOf<Pair<String, Float>>()
        val analyzer = CurrencyAnalyzer(ThrowingRecognizer()) { label, confidence -> results += label to confidence }

        analyzer.analyze(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))

        assertEquals(listOf("" to 0f), results)
    }

    @Test
    fun streamingFrameReturnsEmptyWhenRecognizerThrows() {
        val results = mutableListOf<Pair<String, Float>>()
        val analyzer = CurrencyAnalyzer(ThrowingRecognizer()) { label, confidence -> results += label to confidence }

        analyzer.analyzeStreamingFrame(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))

        assertEquals(listOf("" to 0f), results)
    }

    private class FakeRecognizer(
        private val box: CurrencyAnalyzer.BBox? = CurrencyAnalyzer.BBox(0f, 0f, 1f, 1f),
        private val classification: Pair<String, Float> = "20000" to 0.8f
    ) : CurrencyAnalyzer.CurrencyRecognizer {
        var closed = false

        override fun detect(bitmap: Bitmap): CurrencyAnalyzer.BBox? = box

        override fun classify(cropped: Bitmap): Pair<String, Float> = classification

        override fun close() {
            closed = true
        }
    }

    private class ThrowingRecognizer : CurrencyAnalyzer.CurrencyRecognizer {
        override fun detect(bitmap: Bitmap): CurrencyAnalyzer.BBox = error("boom")

        override fun classify(cropped: Bitmap): Pair<String, Float> = error("boom")

        override fun close() = Unit
    }
}
