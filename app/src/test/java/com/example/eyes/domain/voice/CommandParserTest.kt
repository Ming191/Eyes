package com.example.eyes.domain.voice

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CommandParserTest {

    private lateinit var parser: CommandParser

    @Before
    fun setUp() {
        parser = CommandParser()
    }

    @Test
    fun parse_blankInput_returnsUnknownWithRawText() {
        assertEquals(VoiceIntent.Unknown("   "), parser.parse("   "))
    }

    @Test
    fun parse_openOcrQuick_returnsOpenCameraQuickOcr() {
        assertEquals(
            VoiceIntent.OpenCamera(VoiceCameraTarget.OCR, OcrMode.QUICK),
            parser.parse("mo ocr nhanh")
        )
    }

    @Test
    fun parse_captureOcrQuick_returnsCaptureCameraQuickOcr() {
        assertEquals(
            VoiceIntent.CaptureCamera(VoiceCameraTarget.OCR, OcrMode.QUICK),
            parser.parse("chup ocr nhanh")
        )
    }

    @Test
    fun parse_captureOcrAccurate_returnsCaptureCameraAccurateOcr() {
        assertEquals(
            VoiceIntent.CaptureCamera(VoiceCameraTarget.OCR, OcrMode.ACCURACY),
            parser.parse("chup ocr chinh xac")
        )
    }

    @Test
    fun parse_sceneWithImmediateCue_returnsCaptureScene() {
        assertEquals(
            VoiceIntent.CaptureCamera(VoiceCameraTarget.SCENE_DESCRIPTION),
            parser.parse("mo ta canh luon")
        )
    }

    @Test
    fun parse_moneyWithoutImmediateCue_returnsOpenCurrencyMode() {
        assertEquals(
            VoiceIntent.OpenCamera(VoiceCameraTarget.CURRENCY),
            parser.parse("to tien nay bao nhieu")
        )
    }

    @Test
    fun parse_moneyWithImmediateCue_returnsCaptureCurrency() {
        assertEquals(
            VoiceIntent.CaptureCamera(VoiceCameraTarget.CURRENCY),
            parser.parse("nhan dien tien ngay")
        )
    }

    @Test
    fun parse_objectDetection_returnsOpenObjectDetection() {
        assertEquals(
            VoiceIntent.OpenCamera(VoiceCameraTarget.OBJECT_DETECTION),
            parser.parse("nhan dien vat the")
        )
    }

    @Test
    fun parse_dialEmergencyNumber_returnsDialEmergency() {
        assertEquals(VoiceIntent.DialEmergency("113"), parser.parse("goi 113"))
        assertEquals(VoiceIntent.DialEmergency("115"), parser.parse("mo ban phim goi 115"))
    }

    @Test
    fun parse_emergencyWithoutNumber_returnsEmergencyList() {
        assertEquals(VoiceIntent.OpenEmergencyList, parser.parse("goi khan cap"))
    }

    @Test
    fun parse_speechSpeedPreset_returnsSetSpeechSpeed() {
        assertEquals(VoiceIntent.SetSpeechSpeed(1.25f), parser.parse("toc do doc 1.25"))
        assertEquals(VoiceIntent.SetSpeechSpeed(0.75f), parser.parse("toc do doc 0,75"))
    }

    @Test
    fun parse_speedRelativeCommands_returnIncreaseOrDecrease() {
        assertEquals(VoiceIntent.IncreaseSpeechSpeed, parser.parse("doc nhanh hon"))
        assertEquals(VoiceIntent.DecreaseSpeechSpeed, parser.parse("doc cham hon"))
    }

    @Test
    fun parse_languageCommands_returnSetAppLanguage() {
        assertEquals(VoiceIntent.SetAppLanguage(AppLanguage.VI), parser.parse("chuyen sang tieng viet"))
        assertEquals(VoiceIntent.SetAppLanguage(AppLanguage.EN), parser.parse("doi sang tieng anh"))
    }

    @Test
    fun parse_autoTranslateCommands_returnSetAutoTranslate() {
        assertEquals(VoiceIntent.SetAutoTranslate(false), parser.parse("tat che do tu dong dich"))
        assertEquals(VoiceIntent.SetAutoTranslate(true), parser.parse("bat dich tieng anh sang tieng viet"))
    }

    @Test
    fun parse_priorityCommands_winOverOtherIntents() {
        assertEquals(VoiceIntent.Stop, parser.parse("dung chup ocr nhanh"))
        assertEquals(VoiceIntent.Help, parser.parse("tro giup doc van ban"))
        assertEquals(VoiceIntent.Repeat, parser.parse("doc lai"))
    }

    @Test
    fun parse_unknownInput_preservesRawText() {
        val input = "hom nay troi dep"
        assertEquals(VoiceIntent.Unknown(input), parser.parse(input))
    }

    @Test
    fun parse_englishFallbackStillWorks() {
        assertEquals(
            VoiceIntent.OpenCamera(VoiceCameraTarget.CURRENCY),
            parser.parse("How much is this bill", AppLanguage.EN)
        )
    }
}
