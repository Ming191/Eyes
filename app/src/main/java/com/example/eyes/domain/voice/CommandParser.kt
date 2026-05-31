package com.example.eyes.domain.voice

import com.example.eyes.domain.i18n.AppLanguage
import java.util.Locale

/**
 * Rule-based parser that maps recognized Vietnamese speech to a [VoiceCommand].
 *
 * The parser is deliberately keyword-driven (no ML) so it stays predictable,
 * fast, and easy to test. Matching is case-insensitive and tolerant of filler
 * words ("ờ", "cho tôi", "nhé") and extra whitespace.
 *
 * Priority order when multiple keywords appear in a single utterance:
 *  1. Help      — user is lost; surface guidance
 *  2. Repeat    — user wants the last response again
 *  3. ReadText / DescribeScene / RecognizeCurrency (any order)
 *  4. Unknown   — fallback
 */
class CommandParser {

    fun parse(rawText: String, language: AppLanguage = AppLanguage.VI): VoiceCommand {
        val original = rawText
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return VoiceCommand.Unknown(original)
        }

        val keywordSets = KeywordSet.forLanguage(language).withFallbacks()

        if (keywordSets.any { containsAny(normalized, it.help) }) {
            return VoiceCommand.Help
        }

        if (keywordSets.any { containsAny(normalized, it.repeat) }) {
            return VoiceCommand.Repeat
        }

        if (keywordSets.any { containsAny(normalized, it.readText) }) {
            return VoiceCommand.ReadText
        }
        if (keywordSets.any { containsAny(normalized, it.describeScene) }) {
            return VoiceCommand.DescribeScene
        }
        if (keywordSets.any { containsAny(normalized, it.detectObjects) }) return VoiceCommand.DetectObjects
        if (keywordSets.any { containsAny(normalized, it.openSettings) }) return VoiceCommand.OpenSettings
        if (keywordSets.any { containsAny(normalized, it.openHome) }) return VoiceCommand.OpenHome
        if (keywordSets.any { containsAny(normalized, it.openEmergency) }) return VoiceCommand.OpenEmergency
        if (keywordSets.any { containsAny(normalized, it.ocrAccurate) }) return VoiceCommand.OcrAccurate
        if (keywordSets.any { containsAny(normalized, it.ocrQuick) }) return VoiceCommand.OcrQuick
        if (keywordSets.any { containsAny(normalized, it.recognizeCurrency) }) {
            return VoiceCommand.RecognizeCurrency
        }

        return VoiceCommand.Unknown(original)
    }

    /**
     * Lowercase + collapse whitespace. Locale.ROOT is safe for Vietnamese
     * because no Vietnamese letter has locale-dependent casing rules
     * (unlike Turkish 'I' / 'i').
     */
    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    private companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")

        private data class KeywordSet(
            val help: List<String>,
            val repeat: List<String>,
            val readText: List<String>,
            val describeScene: List<String>,
            val recognizeCurrency: List<String>,
            val detectObjects: List<String>,
            val openHome: List<String>,
            val openSettings: List<String>,
            val openEmergency: List<String>,
            val ocrQuick: List<String>,
            val ocrAccurate: List<String>,
        ) {
            companion object {
                fun forLanguage(language: AppLanguage): KeywordSet = when (language) {
                    AppLanguage.VI -> VI_KEYWORDS
                    AppLanguage.EN -> EN_KEYWORDS
                }
            }
        }

        private fun KeywordSet.withFallbacks(): List<KeywordSet> = when (this) {
            VI_KEYWORDS -> listOf(VI_KEYWORDS, EN_KEYWORDS)
            EN_KEYWORDS -> listOf(EN_KEYWORDS, VI_KEYWORDS)
            else -> listOf(this, VI_KEYWORDS, EN_KEYWORDS)
        }

        private val VI_KEYWORDS = KeywordSet(
            help = listOf(
            "trợ giúp",
            "giúp đỡ",
            "tôi nói được gì",
            "hướng dẫn",
            "có thể nói gì"
            ),

            repeat = listOf(
            "đọc lại",
            "nhắc lại",
            "lặp lại",
            "nói lại"
            ),

            readText = listOf(
            "đọc",
            "đọc giúp",
            "đọc cho",
            "đọc văn bản",
            "đọc chữ",
            "đọc cái này",
            "đọc giùm",
            "có chữ gì"
            ),

            describeScene = listOf(
            "cảnh",
            "trước mặt có gì",
            "phía trước có gì",
            "mô tả",
            "xung quanh có gì",
            "đây là gì",
            "khung cảnh"
            ),

            recognizeCurrency = listOf(
            "tiền",
            "tien",
            "tờ tiền",
            "to tien",
            "tiền này",
            "tien nay",
            "mệnh giá",
            "menh gia",
            "bao nhiêu tiền",
            "bao nhieu tien",
            "nhận diện tiền",
            "nhan dien tien",
            "tiền bao nhiêu"
            ,"tien bao nhieu"
            ),
            detectObjects = listOf("nhận diện vật thể", "vật thể", "có vật gì", "detect objects", "object detection"),
            openHome = listOf("về trang chủ", "mở trang chủ", "trang chủ", "home"),
            openSettings = listOf("mở cài đặt", "cài đặt", "settings", "open settings"),
            openEmergency = listOf("gọi khẩn cấp", "mở gọi khẩn cấp", "khẩn cấp", "emergency"),
            ocrQuick = listOf("đọc nhanh", "ocr nhanh", "chế độ nhanh", "quick ocr"),
            ocrAccurate = listOf("đọc chính xác", "ocr chính xác", "ocr kỹ", "chế độ chính xác", "accurate ocr")
        )

        private val EN_KEYWORDS = KeywordSet(
            help = listOf("help", "what can i say", "commands"),
            repeat = listOf("repeat", "say again", "read again"),
            readText = listOf("read text", "read this", "read for me", "read words"),
            describeScene = listOf("what is in front", "what's in front", "describe", "describe scene", "what is around"),
            recognizeCurrency = listOf("currency", "money", "banknote", "bill", "how much money", "how much is this bill", "recognize money"),
            detectObjects = listOf("detect objects", "object detection", "objects"),
            openHome = listOf("home", "go home", "open home"),
            openSettings = listOf("settings", "open settings"),
            openEmergency = listOf("emergency", "emergency call", "open emergency"),
            ocrQuick = listOf("quick ocr", "read quickly", "quick reading"),
            ocrAccurate = listOf("accurate ocr", "read accurately", "accurate reading")
        )
    }
}
