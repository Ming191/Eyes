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
        val vietnameseLocale = Locale.Builder().setLanguage("vi").setRegion("VN").build()

        assertEquals(AppLanguage.EN, AppLanguage.fromLocale(Locale.UK))
        assertEquals(AppLanguage.VI, AppLanguage.fromLocale(vietnameseLocale))
        assertEquals(AppLanguage.VI, AppLanguage.fromLocale(Locale.FRANCE))
    }

    @Test
    fun languageMetadataMatchesExpectedValues() {
        assertEquals("en", AppLanguage.EN.storageValue)
        assertEquals(Locale.US, AppLanguage.EN.ttsLocale)
        assertEquals(VOICE_INPUT_LANGUAGE_TAG, AppLanguage.EN.sttLanguageTag)
        assertEquals("vi", AppLanguage.VI.storageValue)
        assertEquals(VOICE_INPUT_LANGUAGE_TAG, AppLanguage.VI.sttLanguageTag)
    }

    @Test
    fun sttLanguageTag_allAppLanguages_useVietnameseVoiceInput() {
        AppLanguage.entries.forEach { language ->
            assertEquals(VOICE_INPUT_LANGUAGE_TAG, language.sttLanguageTag)
        }
    }
}
