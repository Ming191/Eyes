package com.example.eyes.objectdetection

import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GridPositionMapperTest {

    private val mapper = GridPositionMapper()

    @Test
    fun map_centerInEachCell_returnsExpectedPosition() {
        // GIVEN
        val frameWidth = 900
        val frameHeight = 600

        // WHEN / THEN
        assertEquals(DetectionPosition.TOP_LEFT, mapper.map(boxAt(150f, 100f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.TOP_CENTER, mapper.map(boxAt(450f, 100f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.TOP_RIGHT, mapper.map(boxAt(750f, 100f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.CENTER_LEFT, mapper.map(boxAt(150f, 300f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.CENTER, mapper.map(boxAt(450f, 300f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.CENTER_RIGHT, mapper.map(boxAt(750f, 300f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.BOTTOM_LEFT, mapper.map(boxAt(150f, 500f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.BOTTOM_CENTER, mapper.map(boxAt(450f, 500f), frameWidth, frameHeight))
        assertEquals(DetectionPosition.BOTTOM_RIGHT, mapper.map(boxAt(750f, 500f), frameWidth, frameHeight))
    }

    @Test
    fun map_centerOnBoundary_usesNextCell() {
        // GIVEN
        val frameWidth = 900
        val frameHeight = 600

        // WHEN
        val firstBoundary = mapper.map(boxAt(300f, 200f), frameWidth, frameHeight)
        val secondBoundary = mapper.map(boxAt(600f, 400f), frameWidth, frameHeight)

        // THEN
        assertEquals(DetectionPosition.CENTER, firstBoundary)
        assertEquals(DetectionPosition.BOTTOM_RIGHT, secondBoundary)
    }

    @Test
    fun map_centerOutsideFrame_clampsToFrame() {
        // GIVEN
        val frameWidth = 900
        val frameHeight = 600

        // WHEN
        val topLeft = mapper.map(boxAt(-50f, -50f), frameWidth, frameHeight)
        val bottomRight = mapper.map(boxAt(950f, 650f), frameWidth, frameHeight)

        // THEN
        assertEquals(DetectionPosition.TOP_LEFT, topLeft)
        assertEquals(DetectionPosition.BOTTOM_RIGHT, bottomRight)
    }

    @Test
    fun map_invalidFrame_throws() {
        // GIVEN
        val box = boxAt(10f, 10f)

        // WHEN / THEN
        assertThrows(IllegalArgumentException::class.java) {
            mapper.map(box, frameWidth = 0, frameHeight = 600)
        }
        assertThrows(IllegalArgumentException::class.java) {
            mapper.map(box, frameWidth = 900, frameHeight = 0)
        }
    }

    @Test
    fun localizedText_supportsVietnameseAndEnglish() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // WHEN
        val vietnamese = DetectionPosition.TOP_LEFT.localizedText(context, AppLanguage.VI)
        val english = DetectionPosition.TOP_LEFT.localizedText(context, AppLanguage.EN)

        // THEN
        assertEquals("góc trên bên trái", vietnamese)
        assertEquals("top left", english)
    }

    private fun boxAt(centerX: Float, centerY: Float): RectF = RectF(
        centerX - 5f,
        centerY - 5f,
        centerX + 5f,
        centerY + 5f
    )
}
