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
 *  2. Help      — user is lost; surface guidance
 *  3. Repeat    — user wants the last response again
 *  4. Navigate  — needs destination extraction, handled before generic verbs
 *  5. ReadText / DescribeScene / RecognizeCurrency / DetectObstacle (any order)
 *  6. Unknown   — fallback, preserves [rawText]
 */
class CommandParser {

    fun parse(rawText: String, language: AppLanguage = AppLanguage.VI): VoiceCommand {
        val original = rawText
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return VoiceCommand.Unknown(original)
        }

        // Priority 1: Stop
        val keywords = KeywordSet.forLanguage(language)

        if (containsAny(normalized, keywords.stop)) {
            return VoiceCommand.Stop
        }

        // Priority 2: Help
        if (containsAny(normalized, keywords.help)) {
            return VoiceCommand.Help
        }

        // Priority 3: Repeat
        if (containsAny(normalized, keywords.repeat)) {
            return VoiceCommand.Repeat
        }

        // Priority 4: Navigate (must extract destination)
        val navigateMatch = keywords.navigateRegex.find(normalized)
        if (navigateMatch != null) {
            val destination = navigateMatch.groupValues[2]
                .replace(WHITESPACE_REGEX, " ")
                .trim()
            return if (destination.isEmpty()) {
                VoiceCommand.Unknown(original)
            } else {
                VoiceCommand.Navigate(destination)
            }
        }

        // Priority 5: feature commands (any order)
        if (containsAny(normalized, keywords.readText)) {
            return VoiceCommand.ReadText
        }
        if (containsAny(normalized, keywords.describeScene)) {
            return VoiceCommand.DescribeScene
        }
        if (containsAny(normalized, keywords.recognizeCurrency)) {
            return VoiceCommand.RecognizeCurrency
        }
        if (containsAny(normalized, keywords.detectObstacle)) {
            return VoiceCommand.DetectObstacle
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
            val navigateRegex: Regex,
            val readText: List<String>,
            val describeScene: List<String>,
            val recognizeCurrency: List<String>,
            val detectObstacle: List<String>
        ) {
            companion object {
                fun forLanguage(language: AppLanguage): KeywordSet = when (language) {
                    AppLanguage.VI -> VI_KEYWORDS
                    AppLanguage.EN -> EN_KEYWORDS
                }
            }
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

        /**
         * Navigate verbs followed by a destination.
         * Group 1: the verb phrase (discarded). Group 2: the destination.
         *
         * Verbs supported (case-insensitive after [normalize]):
         *  - "đi đến"
         *  - "đi tới"
         *  - "đến"           (alone, e.g. "đến hồ Gươm")
         *  - "tới"           (alone)
         *  - "dẫn tôi đến" / "dẫn tôi tới" / "dẫn đường đến" / "dẫn đường tới"
         *
         * The destination is everything after the verb until end-of-string.
         * Trailing politeness particles ("nhé", "đi", "ạ") are stripped below.
         */
            navigateRegex = Regex(
            "(đi đến|đi tới|dẫn tôi đến|dẫn tôi tới|dẫn đường đến|dẫn đường tới|đến|tới)\\s+(.+?)(?:\\s+(?:nhé|đi|ạ|nha))?\\s*$"
            ),

            readText = listOf(
            "đọc giúp",
            "đọc cho",
            "đọc văn bản",
            "đọc chữ",
            "đọc cái này",
            "đọc giùm",
            "có chữ gì"
            ),

            describeScene = listOf(
            "trước mặt có gì",
            "phía trước có gì",
            "mô tả",
            "xung quanh có gì",
            "đây là gì",
            "khung cảnh"
            ),

            recognizeCurrency = listOf(
            "tờ tiền",
            "tiền này",
            "mệnh giá",
            "bao nhiêu tiền",
            "nhận diện tiền",
            "tiền bao nhiêu"
            ),

            detectObstacle = listOf(
            "vật cản",
            "chướng ngại",
            "có gì cản",
            "đi tiếp được không",
            "phía trước có cản"
            )
        )

        private val EN_KEYWORDS = KeywordSet(
            stop = listOf("stop", "cancel", "be quiet", "silence"),
            help = listOf("help", "guide", "what can i say", "commands"),
            repeat = listOf("repeat", "say again", "read again"),
            navigateRegex = Regex(
                "(navigate to|go to|take me to|directions to|guide me to)\\s+(.+?)(?:\\s+(?:please))?\\s*$"
            ),
            readText = listOf("read text", "read this", "read for me", "read words"),
            describeScene = listOf("what is in front", "what's in front", "describe", "describe scene", "what is around"),
            recognizeCurrency = listOf("currency", "money", "banknote", "how much money", "recognize money"),
            detectObstacle = listOf("obstacle", "detect obstacle", "is there an obstacle", "can i move forward")
        )
    }
}
