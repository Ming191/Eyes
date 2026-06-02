package com.example.eyes.infrastructure.system

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TtsTextPreprocessorTest {
    @Test
    fun preprocessVietnameseRewritesTechnicalTermsAndSymbols() {
        val text = "EN->VI | GPT-4o ■ ML Kit ▪ OCR ► API TalkBack Double tap Debug fallback mode https://x.test"

        val result = TtsTextPreprocessor.preprocess(text, Locale("vi", "VN"))

        assertEquals(
            "tiếng Anh sang tiếng Việt GPT bốn ô em eo kít ô xê e rờ ây pi ai Talk Back chạm hai lần gỡ lỗi chuyển dự phòng chế độ link",
            result
        )
    }

    @Test
    fun preprocessEnglishRewritesTermsForEnglishLocale() {
        val text = "EN —> VI GPT 4o ML Kit OCR API TalkBack Double tap"

        val result = TtsTextPreprocessor.preprocess(text, Locale.ENGLISH)

        assertEquals("English to Vietnamese G P T four O M L Kit O C R A P I TalkBack double tap", result)
    }
}
