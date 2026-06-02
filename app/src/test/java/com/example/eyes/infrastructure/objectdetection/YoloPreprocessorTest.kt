package com.example.eyes.infrastructure.objectdetection

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class YoloPreprocessorTest {
    @Test
    fun preprocess_returnsChannelFirstNormalizedRgb() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.rgb(255, 0, 0))
        bitmap.setPixel(1, 0, Color.rgb(0, 128, 0))
        bitmap.setPixel(0, 1, Color.rgb(0, 0, 64))
        bitmap.setPixel(1, 1, Color.rgb(255, 255, 255))

        val result = YoloPreprocessor(inputSize = 2).preprocess(bitmap)

        assertEquals(12, result.size)
        assertEquals(1f, result[0], 0.0001f)
        assertEquals(0f, result[1], 0.0001f)
        assertEquals(0f, result[2], 0.0001f)
        assertEquals(1f, result[3], 0.0001f)
        assertEquals(0f, result[4], 0.0001f)
        assertEquals(128 / 255f, result[5], 0.0001f)
        assertEquals(0f, result[6], 0.0001f)
        assertEquals(1f, result[7], 0.0001f)
        assertEquals(0f, result[8], 0.0001f)
        assertEquals(0f, result[9], 0.0001f)
        assertEquals(64 / 255f, result[10], 0.0001f)
        assertEquals(1f, result[11], 0.0001f)
    }
}
