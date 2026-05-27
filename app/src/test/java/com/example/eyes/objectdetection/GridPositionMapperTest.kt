package com.example.eyes.objectdetection

import androidx.test.core.app.ApplicationProvider
import com.example.eyes.domain.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GridPositionMapperTest {

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
}
