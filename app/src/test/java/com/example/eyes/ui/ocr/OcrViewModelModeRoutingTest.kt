package com.example.eyes.ui.ocr

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.data.DataStoreManager
import com.example.eyes.ocr.OcrEngine
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ocr.OcrResult
import com.example.eyes.ocr.OcrTranslator
import com.example.eyes.system.HapticService
import com.example.eyes.system.TtsService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OcrViewModelModeRoutingTest {

    @Before
    fun setUp() {
        runCatching { stopKoin() }
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun recognizeCapturedBitmap_accuracyModeSuccess_usesAccuracyEngine() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val quickEngine = FakeOcrEngine(result = OcrResult("quick", listOf("quick")))
        val accuracyEngine = FakeOcrEngine(result = OcrResult("accuracy", listOf("accuracy")))

        val viewModel = OcrViewModel(
            quickOcrEngine = quickEngine,
            accuracyOcrEngine = accuracyEngine,
            translator = FakeTranslator(),
            dataStoreManager = DataStoreManager(context),
            tts = TtsService(context),
            haptic = HapticService(context)
        )

        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val outcome = viewModel.recognizeCapturedBitmap(bitmap, OcrMode.ACCURACY)

        assertTrue(outcome.result.isSuccess)
        assertEquals("accuracy", outcome.result.getOrThrow().fullText)
        assertEquals(false, outcome.usedFallbackFromAccuracy)
        assertEquals(0, quickEngine.bitmapRecognizeCalls)
        assertEquals(1, accuracyEngine.bitmapRecognizeCalls)
    }

    @Test
    fun recognizeCapturedBitmap_accuracyModeFailure_fallsBackToQuickEngine() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val quickEngine = FakeOcrEngine(result = OcrResult("fallback", listOf("fallback")))
        val accuracyEngine = FakeOcrEngine(error = IllegalStateException("network"))

        val viewModel = OcrViewModel(
            quickOcrEngine = quickEngine,
            accuracyOcrEngine = accuracyEngine,
            translator = FakeTranslator(),
            dataStoreManager = DataStoreManager(context),
            tts = TtsService(context),
            haptic = HapticService(context)
        )

        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val outcome = viewModel.recognizeCapturedBitmap(bitmap, OcrMode.ACCURACY)

        assertTrue(outcome.result.isSuccess)
        assertEquals("fallback", outcome.result.getOrThrow().fullText)
        assertEquals(true, outcome.usedFallbackFromAccuracy)
        assertEquals(1, accuracyEngine.bitmapRecognizeCalls)
        assertEquals(1, quickEngine.bitmapRecognizeCalls)
    }

    @Test
    fun recognizeCapturedBitmap_quickModeUsesQuickEngineOnly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val quickEngine = FakeOcrEngine(result = OcrResult("quick", listOf("quick")))
        val accuracyEngine = FakeOcrEngine(result = OcrResult("accuracy", listOf("accuracy")))

        val viewModel = OcrViewModel(
            quickOcrEngine = quickEngine,
            accuracyOcrEngine = accuracyEngine,
            translator = FakeTranslator(),
            dataStoreManager = DataStoreManager(context),
            tts = TtsService(context),
            haptic = HapticService(context)
        )

        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val outcome = viewModel.recognizeCapturedBitmap(bitmap, OcrMode.QUICK)

        assertTrue(outcome.result.isSuccess)
        assertEquals("quick", outcome.result.getOrThrow().fullText)
        assertEquals(false, outcome.usedFallbackFromAccuracy)
        assertEquals(1, quickEngine.bitmapRecognizeCalls)
        assertEquals(0, accuracyEngine.bitmapRecognizeCalls)
    }

    private class FakeOcrEngine(
        private val result: OcrResult? = null,
        private val error: Throwable? = null
    ) : OcrEngine {
        var bitmapRecognizeCalls: Int = 0

        override suspend fun recognize(imageProxy: ImageProxy): OcrResult {
            imageProxy.close()
            throw UnsupportedOperationException("Not needed for this test")
        }

        override suspend fun recognize(bitmap: Bitmap): OcrResult {
            bitmapRecognizeCalls++
            error?.let { throw it }
            return result ?: OcrResult.EMPTY
        }

        override fun close() = Unit
    }

    private class FakeTranslator : OcrTranslator {
        override suspend fun translateToVietnamese(text: String): String = text
    }
}
