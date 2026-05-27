package com.example.eyes.domain.voice

import com.example.eyes.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CommandParser]. Pure JVM — no Android framework, no Robolectric.
 *
 * Test naming convention follows the codebase style:
 *   methodName_condition_expectedResult
 *
 * Each test follows the GIVEN / WHEN / THEN structure used elsewhere
 * in the project (see HomeScreenSemanticsTest, FrameThrottleTest).
 *
 * Test names use ASCII identifiers for safe handling on every CI runner;
 * Vietnamese test inputs live in the string literals.
 */
class CommandParserTest {

    private lateinit var parser: CommandParser

    @Before
    fun setUp() {
        parser = CommandParser()
    }

    // ----- Empty / blank input -----

    @Test
    fun parse_emptyString_returnsUnknownWithEmptyRawText() {
        // GIVEN
        val input = ""
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.Unknown(""), result)
    }

    @Test
    fun parse_blankString_returnsUnknownPreservingRawText() {
        // GIVEN
        val input = "   "
        // WHEN
        val result = parser.parse(input)
        // THEN — raw text preserved verbatim so logs/UI can echo what was heard
        assertEquals(VoiceCommand.Unknown("   "), result)
    }

    // ----- ReadText -----

    @Test
    fun parse_readText_phrase1_returnsReadText() {
        // GIVEN
        val input = "đọc giúp tôi"
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.ReadText, result)
    }

    @Test
    fun parse_readText_phrase2_returnsReadText() {
        // GIVEN
        val input = "đọc văn bản này"
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.ReadText, result)
    }

    @Test
    fun parse_readText_uppercaseInput_returnsReadText() {
        // GIVEN — STT engines occasionally capitalize the first word
        val input = "ĐỌC giúp TÔI"
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.ReadText, result)
    }

    // ----- DescribeScene -----

    @Test
    fun parse_describeScene_phrase1_returnsDescribeScene() {
        // GIVEN
        val input = "trước mặt có gì"
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.DescribeScene, result)
    }

    @Test
    fun parse_describeScene_phrase2_returnsDescribeScene() {
        assertEquals(VoiceCommand.DescribeScene, parser.parse("mô tả xung quanh"))
    }

    @Test
    fun parse_describeScene_phiaTruoc_returnsDescribeScene() {
        assertEquals(VoiceCommand.DescribeScene, parser.parse("phía trước có gì"))
    }

    // ----- RecognizeCurrency -----

    @Test
    fun parse_currency_phrase1_returnsRecognizeCurrency() {
        // GIVEN
        val input = "tờ tiền này bao nhiêu"
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.RecognizeCurrency, result)
    }

    @Test
    fun parse_currency_menhGia_returnsRecognizeCurrency() {
        assertEquals(VoiceCommand.RecognizeCurrency, parser.parse("đây là tờ mệnh giá gì"))
    }

    // ----- Removed navigation commands -----

    @Test
    fun parse_navigate_diDen_returnsUnknown() {
        val input = "đi đến bệnh viện Bạch Mai"
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input))
    }

    @Test
    fun parse_navigate_den_returnsUnknown() {
        val input = "đến hồ Gươm"
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input))
    }

    @Test
    fun parse_navigate_danToiToi_returnsUnknown() {
        val input = "dẫn tôi tới chợ Đồng Xuân"
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input))
    }

    @Test
    fun parse_navigate_emptyDestination_returnsUnknown() {
        val input = "đi đến"
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input))
    }

    @Test
    fun parse_navigate_extraWhitespaceInDestination_returnsUnknown() {
        val input = "đi đến    bệnh viện   Bạch Mai   "
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input))
    }

    @Test
    fun parse_navigate_trailingPolitenessParticle_returnsUnknown() {
        val input = "đi đến hồ Gươm nhé"
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input))
    }

    // ----- Repeat -----

    @Test
    fun parse_repeat_docLai_returnsRepeat() {
        assertEquals(VoiceCommand.Repeat, parser.parse("đọc lại"))
    }

    @Test
    fun parse_repeat_nhacLaiDi_returnsRepeat() {
        assertEquals(VoiceCommand.Repeat, parser.parse("nhắc lại đi"))
    }

    // ----- Stop -----

    @Test
    fun parse_stop_dungLai_returnsStop() {
        assertEquals(VoiceCommand.Stop, parser.parse("dừng lại"))
    }

    @Test
    fun parse_stop_imLang_returnsStop() {
        assertEquals(VoiceCommand.Stop, parser.parse("im lặng"))
    }

    // ----- Help -----

    @Test
    fun parse_help_troGiup_returnsHelp() {
        assertEquals(VoiceCommand.Help, parser.parse("trợ giúp"))
    }

    @Test
    fun parse_help_toiNoiDuocGi_returnsHelp() {
        assertEquals(VoiceCommand.Help, parser.parse("tôi nói được gì"))
    }

    // ----- Robustness: filler words, whitespace, unknown -----

    @Test
    fun parse_fillerWordsAroundCommand_stillRecognized() {
        // GIVEN — user hesitates with "ờ" and adds polite "nhé"
        val input = "ờ cho tôi đọc giúp nhé"
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.ReadText, result)
    }

    @Test
    fun parse_extraWhitespaceAroundCommand_stillRecognized() {
        // GIVEN
        val input = "   đọc   giúp   tôi   "
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.ReadText, result)
    }

    @Test
    fun parse_unrecognizedInput_returnsUnknownPreservingRawText() {
        // GIVEN
        val input = "hôm nay trời đẹp"
        // WHEN
        val result = parser.parse(input)
        // THEN — raw text is preserved exactly (including original casing)
        //         so logs and UI can echo what was actually heard
        assertEquals(VoiceCommand.Unknown("hôm nay trời đẹp"), result)
    }

    // ----- Priority: Stop and Help win over other intents -----

    @Test
    fun parse_stopAndNavigateInSameUtterance_stopWins() {
        // GIVEN — Stop must take priority for safety even if other verbs are present
        val input = "dừng đi đến chợ"
        // WHEN
        val result = parser.parse(input)
        // THEN
        assertEquals(VoiceCommand.Stop, result)
    }

    @Test
    fun parse_helpAndReadInSameUtterance_helpWins() {
        // GIVEN
        val input = "trợ giúp đọc văn bản"
        // WHEN
        val result = parser.parse(input)
        // THEN — Help is higher priority than ReadText
        assertEquals(VoiceCommand.Help, result)
    }

    @Test
    fun parse_englishReadText_returnsReadText() {
        // GIVEN
        val input = "read this"
        // WHEN
        val result = parser.parse(input, AppLanguage.EN)
        // THEN
        assertEquals(VoiceCommand.ReadText, result)
    }

    @Test
    fun parse_englishNavigate_returnsUnknown() {
        val input = "navigate to Central Park"
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input, AppLanguage.EN))
    }

    @Test
    fun parse_englishGuideMeTo_returnsUnknown() {
        val input = "guide me to Central Park"
        assertEquals(VoiceCommand.Unknown(input), parser.parse(input, AppLanguage.EN))
    }

    @Test
    fun parse_englishHowMuchIsThisBill_returnsRecognizeCurrency() {
        // GIVEN
        val input = "How much is this bill"
        // WHEN
        val result = parser.parse(input, AppLanguage.EN)
        // THEN
        assertEquals(VoiceCommand.RecognizeCurrency, result)
    }

    @Test
    fun parse_englishStopAndHelp_havePriority() {
        assertEquals(VoiceCommand.Stop, parser.parse("stop and navigate to market", AppLanguage.EN))
        assertEquals(VoiceCommand.Help, parser.parse("help read text", AppLanguage.EN))
    }
}
