package com.example.eyes.domain.voice

import com.example.eyes.i18n.AppLanguage
import java.util.Locale

/**
 * Rule-based parser that maps recognized Vietnamese speech to a [VoiceCommand].
 *
 * The parser is deliberately keyword-driven (no ML) so it stays predictable,
 * fast, and easy to test. Matching is case-insensitive and tolerant of filler
 * words ("ờ", "cho tôi", "nhé") and extra whitespace.
 *
 * Priority order when multiple keywords appear in a single utterance:
 *  1. Stop      — safety: should always interrupt
 *  3. Help      — user is lost; surface guidance
 *  4. Repeat    — user wants the last response again
 *  5. ReadText / DescribeScene / RecognizeCurrency (any order)
 *  6. Unknown   — fallback
 */
class CommandParser {

    fun parse(rawText: String, language: AppLanguage = AppLanguage.VI): VoiceCommand {
        val original = rawText
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return VoiceCommand.Unknown(original)
        }

        val keywordSets = KeywordSet.forLanguage(language).withFallbacks()

        if (keywordSets.any { containsAny(normalized, it.stop) }) {
            return VoiceCommand.Stop
        }

        // Priority 3: Help
        if (keywordSets.any { containsAny(normalized, it.help) }) {
            return VoiceCommand.Help
        }

        // Priority 4: Repeat
        if (keywordSets.any { containsAny(normalized, it.repeat) }) {
            return VoiceCommand.Repeat
        }

        // Priority 5: feature commands (any order)
        if (keywordSets.any { containsAny(normalized, it.readText) }) {
            return VoiceCommand.ReadText
        }
        if (keywordSets.any { containsAny(normalized, it.describeScene) }) {
            return VoiceCommand.DescribeScene
        }
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
            val stop: List<String>,
            val help: List<String>,
            val repeat: List<String>,
            val readText: List<String>,
            val describeScene: List<String>,
            val recognizeCurrency: List<String>,
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

        // Stop is checked first; "dừng" alone is enough.
        private val VI_KEYWORDS = KeywordSet(
            stop = listOf(
            "dừng",
            "im lặng",
            "thôi không",
            "bỏ qua"
            ),

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
            )
        )

        private val EN_KEYWORDS = KeywordSet(
            stop = listOf("stop", "cancel", "be quiet", "silence"),
            help = listOf("help", "what can i say", "commands"),
            repeat = listOf("repeat", "say again", "read again"),
            readText = listOf("read text", "read this", "read for me", "read words"),
            describeScene = listOf("what is in front", "what's in front", "describe", "describe scene", "what is around"),
            recognizeCurrency = listOf("currency", "money", "banknote", "bill", "how much money", "how much is this bill", "recognize money")
        )
    }
}
