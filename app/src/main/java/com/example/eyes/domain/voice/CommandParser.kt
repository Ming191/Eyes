package com.example.eyes.domain.voice

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

    fun parse(rawText: String): VoiceCommand {
        val original = rawText
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return VoiceCommand.Unknown(original)
        }

        // Priority 1: Stop
        if (containsAny(normalized, STOP_KEYWORDS)) {
            return VoiceCommand.Stop
        }

        // Priority 2: Help
        if (containsAny(normalized, HELP_KEYWORDS)) {
            return VoiceCommand.Help
        }

        // Priority 3: Repeat
        if (containsAny(normalized, REPEAT_KEYWORDS)) {
            return VoiceCommand.Repeat
        }

        // Priority 4: Navigate (must extract destination)
        val navigateMatch = NAVIGATE_REGEX.find(normalized)
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
        if (containsAny(normalized, READ_TEXT_KEYWORDS)) {
            return VoiceCommand.ReadText
        }
        if (containsAny(normalized, DESCRIBE_SCENE_KEYWORDS)) {
            return VoiceCommand.DescribeScene
        }
        if (containsAny(normalized, RECOGNIZE_CURRENCY_KEYWORDS)) {
            return VoiceCommand.RecognizeCurrency
        }
        if (containsAny(normalized, DETECT_OBSTACLE_KEYWORDS)) {
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

        // Stop is checked first; "dừng" alone is enough.
        private val STOP_KEYWORDS = listOf(
            "dừng",
            "im lặng",
            "thôi không",
            "bỏ qua"
        )

        private val HELP_KEYWORDS = listOf(
            "trợ giúp",
            "giúp đỡ",
            "tôi nói được gì",
            "hướng dẫn",
            "có thể nói gì"
        )

        private val REPEAT_KEYWORDS = listOf(
            "đọc lại",
            "nhắc lại",
            "lặp lại",
            "nói lại"
        )

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
        private val NAVIGATE_REGEX = Regex(
            "(đi đến|đi tới|dẫn tôi đến|dẫn tôi tới|dẫn đường đến|dẫn đường tới|đến|tới)\\s+(.+?)(?:\\s+(?:nhé|đi|ạ|nha))?\\s*$"
        )

        private val READ_TEXT_KEYWORDS = listOf(
            "đọc giúp",
            "đọc cho",
            "đọc văn bản",
            "đọc chữ",
            "đọc cái này",
            "đọc giùm",
            "có chữ gì"
        )

        private val DESCRIBE_SCENE_KEYWORDS = listOf(
            "trước mặt có gì",
            "phía trước có gì",
            "mô tả",
            "xung quanh có gì",
            "đây là gì",
            "khung cảnh"
        )

        private val RECOGNIZE_CURRENCY_KEYWORDS = listOf(
            "tờ tiền",
            "tiền này",
            "mệnh giá",
            "bao nhiêu tiền",
            "nhận diện tiền",
            "tiền bao nhiêu"
        )

        private val DETECT_OBSTACLE_KEYWORDS = listOf(
            "vật cản",
            "chướng ngại",
            "có gì cản",
            "đi tiếp được không",
            "phía trước có cản"
        )
    }
}