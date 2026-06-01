package com.example.eyes.domain.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun sttLanguageTag_allAppLanguages_useVietnameseVoiceInput() {
        AppLanguage.entries.forEach { language ->
            assertEquals(VOICE_INPUT_LANGUAGE_TAG, language.sttLanguageTag)
        }
    }
}
