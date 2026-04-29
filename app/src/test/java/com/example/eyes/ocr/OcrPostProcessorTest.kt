package com.example.eyes.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPostProcessorTest {

    @Test
    fun normalizeText_collapsesWhitespaceAndTrims() {
        val result = OcrPostProcessor.normalizeText("  Xin\n\n chao\u00A0  ban   ")

        assertEquals("Xin chao ban", result)
    }

    @Test
    fun applyLightCorrections_replacesCommonOCRAbbreviations() {
        val result = OcrPostProcessor.applyLightCorrections("ko dc mk")

        assertEquals("không được mình", result)
    }

    @Test
    fun splitToSentences_filtersShortSentenceFragments() {
        val text = "OK. Xin chao ban. A."

        val result = OcrPostProcessor.splitToSentences(text)

        assertEquals(listOf("Xin chao ban"), result)
    }

    @Test
    fun process_returnsNormalizedTextAndSentences() {
        val result = OcrPostProcessor.process("  Xe buyt den.  Moi len xe! ")

        assertEquals("Xe buyt den. Moi len xe!", result.fullText)
        assertEquals(listOf("Xe buyt den", "Moi len xe"), result.sentences)
    }

    @Test
    fun similarityRatio_lowerWhenTextChangesSignificantly() {
        val ratio = OcrPostProcessor.similarityRatio("Cua so da dong", "Khong phai cua so")

        assertTrue(ratio < 0.7f)
    }
}
