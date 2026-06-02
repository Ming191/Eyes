package com.example.eyes.domain.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun fromStorageValueReturnsMatchingLanguageOrVietnameseDefault() {
        assertEquals(AppLanguage.EN, AppLanguage.fromStorageValue("en"))
        assertEquals(AppLanguage.VI, AppLanguage.fromStorageValue("vi"))
        assertEquals(AppLanguage.VI, AppLanguage.fromStorageValue(null))
        assertEquals(AppLanguage.VI, AppLanguage.fromStorageValue("unknown"))
    }

    @Test
    fun fromLocaleUsesEnglishOnlyWhenLanguageIsEnglish() {
        assertEquals(AppLanguage.EN, AppLanguage.fromLocale(Locale.UK))
        assertEquals(AppLanguage.VI, AppLanguage.fromLocale(Locale("vi", "VN")))
        assertEquals(AppLanguage.VI, AppLanguage.fromLocale(Locale.FRANCE))
    }

    @Test
    fun languageMetadataMatchesExpectedTags() {
        assertEquals("en", AppLanguage.EN.storageValue)
        assertEquals("en-US", AppLanguage.EN.sttLanguageTag)
        assertEquals(Locale.US, AppLanguage.EN.ttsLocale)
        assertEquals("vi", AppLanguage.VI.storageValue)
        assertEquals("vi-VN", AppLanguage.VI.sttLanguageTag)
    }
}
