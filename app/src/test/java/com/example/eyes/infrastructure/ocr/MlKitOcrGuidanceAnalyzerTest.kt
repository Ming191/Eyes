package com.example.eyes.infrastructure.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MlKitOcrGuidanceAnalyzerTest {
    @Test
    fun normalizedBoundsClampToFrame() {
        val bounds = Rect(-10, 20, 120, 80).toNormalizedOcrBounds(width = 100, height = 200)

        assertEquals(0f, bounds.left, 0.0001f)
        assertEquals(0.1f, bounds.top, 0.0001f)
        assertEquals(1f, bounds.right, 0.0001f)
        assertEquals(0.4f, bounds.bottom, 0.0001f)
    }

    @Test
    fun averageLuminanceUsesRgbWeights() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.BLACK)
        bitmap.setPixel(1, 0, Color.WHITE)

        assertEquals(0.5f, bitmap.computeAverageLuminance(), 0.0001f)
    }
}
