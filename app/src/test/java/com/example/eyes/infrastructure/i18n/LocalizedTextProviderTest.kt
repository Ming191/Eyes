package com.example.eyes.infrastructure.i18n

import androidx.test.core.app.ApplicationProvider
import com.example.eyes.R
import com.example.eyes.domain.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalizedTextProviderTest {
    @Test
    fun getString_returnsLanguageSpecificResource() {
        val provider = AndroidLocalizedTextProvider(ApplicationProvider.getApplicationContext())

        assertEquals("Change to English.", provider.getString(R.string.settings_language_changed_english, AppLanguage.EN))
        assertEquals("Đã chuyển sang tiếng Việt.", provider.getString(R.string.settings_language_changed_vietnamese, AppLanguage.VI))
    }

    @Test
    fun localizedContext_usesRequestedLocale() {
        val provider = AndroidLocalizedTextProvider(ApplicationProvider.getApplicationContext())

        assertEquals("en", provider.localizedContext(AppLanguage.EN).resources.configuration.locales[0].language)
        assertEquals("vi", provider.localizedContext(AppLanguage.VI).resources.configuration.locales[0].language)
    }
}
