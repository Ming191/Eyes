package com.example.eyes.infrastructure.ocr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.eyes.domain.image.ImageFormat
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.ocr.OcrGuidanceFrame
import com.example.eyes.domain.ocr.OcrResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MlKitOcrSeamTest {
    @Test
    fun engineRecognizeBitmapUsesInjectedRecognizerAndCloseAction() = runTest {
        var closed = false
        val engine = MlKitOcrEngine(
            bitmapRecognizer = { bitmap ->
                assertEquals(3, bitmap.width)
                OcrResult(fullText = "processed", sentences = listOf("processed"))
            },
            closeAction = { closed = true }
        )

        val result = engine.recognize(Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888))
        engine.close()

        assertEquals("processed", result.fullText)
        assertTrue(closed)
    }

    @Test
    fun engineRecognizeImageFrameConvertsBitmapBeforeInjectedRecognizer() = runTest {
        val bytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                .compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.toByteArray()
        }
        val engine = MlKitOcrEngine(
            bitmapRecognizer = { bitmap ->
                OcrResult(fullText = "${bitmap.width}x${bitmap.height}", sentences = emptyList())
            }
        )

        val result = engine.recognize(ImageFrame(bytes, 6, 4, ImageFormat.JPEG))

        assertEquals("6x4", result.fullText)
    }

    @Test
    fun guidanceAnalyzeBitmapUsesInjectedAnalyzerAndCloseAction() = runTest {
        var closed = false
        val analyzer = MlKitOcrGuidanceAnalyzer(
            textAnalyzer = { bitmap ->
                OcrGuidanceFrame(textBounds = null, lineCount = bitmap.width, textLength = bitmap.height, luminance = 0.5f)
            },
            closeAction = { closed = true }
        )

        val frame = analyzer.analyze(Bitmap.createBitmap(4, 5, Bitmap.Config.ARGB_8888))
        analyzer.close()

        assertEquals(4, frame.lineCount)
        assertEquals(5, frame.textLength)
        assertTrue(closed)
    }
}
