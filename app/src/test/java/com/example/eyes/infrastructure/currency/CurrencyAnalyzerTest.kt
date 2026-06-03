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
    fun analyzeReturnsLabelWhenRecognizerConfidenceHigh() {
        val results = mutableListOf<Pair<String, Float>>()
        val analyzer = CurrencyAnalyzer(FakeRecognizer(recognition = "50000" to 0.91f)) { label, confidence ->
            results += label to confidence
        }

        analyzer.analyze(Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) })

        assertEquals(listOf("50000" to 0.91f), results)
    }

    @Test
    fun analyzeReturnsEmptyWhenRecognitionMissingOrConfidenceLow() {
        val results = mutableListOf<Pair<String, Float>>()
        val noDetection = CurrencyAnalyzer(FakeRecognizer(recognition = null)) { label, confidence -> results += label to confidence }
        val lowConfidence = CurrencyAnalyzer(FakeRecognizer(recognition = "10000" to 0.69f)) { label, confidence ->
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
        val analyzer = CurrencyAnalyzer(FakeRecognizer(recognition = "20000" to 0.9f)) { label, confidence ->
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
    fun streamingFrameHandlesMissingDetection() {
        val results = mutableListOf<Pair<String, Float>>()
        val missing = CurrencyAnalyzer(FakeRecognizer(recognition = null)) { label, confidence -> results += label to confidence }
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        repeat(3) { missing.analyzeStreamingFrame(bitmap) }

        assertEquals(listOf("" to 0f, "" to 0f, "" to 0f), results)
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
        private val recognition: Pair<String, Float>? = "20000" to 0.8f
    ) : CurrencyAnalyzer.CurrencyRecognizer {
        var closed = false

        override fun recognize(bitmap: Bitmap): Pair<String, Float>? = recognition

        override fun close() {
            closed = true
        }
    }

    private class ThrowingRecognizer : CurrencyAnalyzer.CurrencyRecognizer {
        override fun recognize(bitmap: Bitmap): Pair<String, Float> = error("boom")

        override fun close() = Unit
    }
}
