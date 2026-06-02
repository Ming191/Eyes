package com.example.eyes.domain.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImageFrameTest {
    @Test
    fun equalsUsesByteArrayContents() {
        val first = ImageFrame(byteArrayOf(1, 2, 3), 10, 20, ImageFormat.JPEG, 90, 123L)
        val second = ImageFrame(byteArrayOf(1, 2, 3), 10, 20, ImageFormat.JPEG, 90, 123L)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun equalsDetectsDifferentDataAndMetadata() {
        val frame = ImageFrame(byteArrayOf(1, 2, 3), 10, 20, ImageFormat.JPEG, 90, 123L)

        assertNotEquals(frame, frame.copy(data = byteArrayOf(1, 2, 4)))
        assertNotEquals(frame, frame.copy(width = 11))
        assertNotEquals(frame, frame.copy(height = 21))
        assertNotEquals(frame, frame.copy(format = ImageFormat.NV21))
        assertNotEquals(frame, frame.copy(rotationDegrees = 0))
        assertNotEquals(frame, frame.copy(timestampMillis = null))
    }
}
