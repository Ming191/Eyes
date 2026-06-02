package com.example.eyes.infrastructure.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.eyes.domain.image.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ImageProxyExtTest {
    @Test
    fun toImageFrameEncodesBitmapMetadataAndBytes() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)

        val frame = bitmap.toImageFrame(format = ImageFormat.RGBA_8888, quality = 100, timestampMillis = 42L)

        assertEquals(3, frame.width)
        assertEquals(2, frame.height)
        assertEquals(ImageFormat.RGBA_8888, frame.format)
        assertEquals(0, frame.rotationDegrees)
        assertEquals(42L, frame.timestampMillis)
        assertTrue(frame.data.isNotEmpty())
    }

    @Test
    fun toBitmapDecodesImageFrameBytes() {
        val source = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.BLUE)
        val frame = source.toImageFrame(format = ImageFormat.RGBA_8888, quality = 100)

        val decoded = frame.toBitmap()

        assertEquals(4, decoded.width)
        assertEquals(3, decoded.height)
    }

    @Test
    fun androidCameraImageConverterConvertsBitmapToImageFrame() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val converter = AndroidCameraImageConverter()

        val frame = converter.toImageFrame(bitmap)

        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertTrue(frame.data.isNotEmpty())
    }
}
