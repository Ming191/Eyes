package com.example.eyes.domain.voice

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.ocr.OcrMode
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

class CommandParser {

    fun parse(rawText: String, language: AppLanguage = AppLanguage.VI): VoiceIntent {
        val text = NormalizedText.from(rawText)
        if (text.folded.isBlank()) return VoiceIntent.Unknown(rawText)

        parsePriorityCommand(text)?.let { return it }
        parseDialEmergency(text)?.let { return it }
        parseSettingsCommand(text)?.let { return it }
        parseNavigationCommand(text)?.let { return it }
        parseCameraCommand(text)?.let { return it }

        return VoiceIntent.Unknown(rawText)
    }

    private fun parsePriorityCommand(text: NormalizedText): VoiceIntent? = when {
        text.containsAny("dung", "dung lai", "thoi", "im lang", "stop", "silence") -> VoiceIntent.Stop
        text.containsAny("tro giup", "giup do", "huong dan", "co the noi gi", "help", "commands") -> VoiceIntent.Help
        text.containsAny("doc lai", "nhac lai", "lap lai", "noi lai", "repeat", "say again", "read again") -> VoiceIntent.Repeat
        else -> null
    }

    private fun parseDialEmergency(text: NormalizedText): VoiceIntent? {
        val number = EMERGENCY_DIAL_REGEX.find(text.folded)?.groupValues?.getOrNull(1) ?: return null
        return VoiceIntent.DialEmergency(number)
    }

    private fun parseSettingsCommand(text: NormalizedText): VoiceIntent? {
        parseSpeechSpeed(text)?.let { return it }
        parseAutoTranslate(text)?.let { return it }
        parseAppLanguage(text)?.let { return it }
        return null
    }

    private fun parseSpeechSpeed(text: NormalizedText): VoiceIntent? {
        val isSpeedCommand = text.containsAny(
            "toc do",
            "toc do doc",
            "toc do noi",
            "speech speed",
            "doc nhanh hon",
            "doc cham hon"
        )
        if (!isSpeedCommand) return null

        if (text.containsAny("nhanh hon", "tang toc do", "tang toc", "faster", "increase speed")) {
            return VoiceIntent.IncreaseSpeechSpeed
        }
        if (text.containsAny("cham hon", "giam toc do", "giam toc", "slower", "decrease speed")) {
            return VoiceIntent.DecreaseSpeechSpeed
        }

        val rawSpeed = SPEED_REGEX.find(text.folded)?.value?.replace(',', '.') ?: return null
        val parsed = rawSpeed.toFloatOrNull() ?: return null
        val preset = TTS_SPEED_PRESETS.minByOrNull { abs(it - parsed) } ?: return null
        return if (abs(preset - parsed) <= SPEED_MATCH_TOLERANCE) {
            VoiceIntent.SetSpeechSpeed(preset)
        } else {
            null
        }
    }

    private fun parseAppLanguage(text: NormalizedText): VoiceIntent? {
        val isLanguageCommand = text.containsAny("ngon ngu", "chuyen sang", "doi sang", "tieng", "language")
        if (!isLanguageCommand) return null

        return when {
            text.containsAny("tieng viet", "vietnamese", "vietnam") -> VoiceIntent.SetAppLanguage(AppLanguage.VI)
            text.containsAny("tieng anh", "english") -> VoiceIntent.SetAppLanguage(AppLanguage.EN)
            else -> null
        }
    }

    private fun parseAutoTranslate(text: NormalizedText): VoiceIntent? {
        val isTranslateCommand = text.containsAny("dich", "tu dong dich", "anh sang viet", "en sang vi", "translate")
        if (!isTranslateCommand) return null

        return when {
            text.containsAny("tat", "huy", "khong dich", "off", "disable", "turn off") -> VoiceIntent.SetAutoTranslate(false)
            text.containsAny("bat", "mo", "tu dong", "on", "enable", "turn on") -> VoiceIntent.SetAutoTranslate(true)
            else -> null
        }
    }

    private fun parseNavigationCommand(text: NormalizedText): VoiceIntent? = when {
        text.containsAny("trang chu", "ve trang chu", "mo trang chu", "home", "go home", "open home") -> VoiceIntent.OpenHome
        text.containsAny("cai dat", "thiet lap", "settings", "open settings") -> VoiceIntent.OpenSettings
        text.containsAny("khan cap", "goi khan cap", "emergency", "emergency call") -> VoiceIntent.OpenEmergencyList
        else -> null
    }

    private fun parseCameraCommand(text: NormalizedText): VoiceIntent? {
        val target = detectCameraTarget(text) ?: return null
        val ocrMode = if (target == VoiceCameraTarget.OCR) detectOcrMode(text) else null
        return if (hasCaptureCue(text)) {
            VoiceIntent.CaptureCamera(target = target, ocrMode = ocrMode)
        } else {
            VoiceIntent.OpenCamera(target = target, ocrMode = ocrMode)
        }
    }

    private fun detectCameraTarget(text: NormalizedText): VoiceCameraTarget? = when {
        text.containsAny("tien", "to tien", "menh gia", "bao nhieu tien", "currency", "money", "banknote", "bill") ->
            VoiceCameraTarget.CURRENCY

        text.containsAny("vat the", "do vat", "co vat gi", "nhan dien vat", "object", "objects") ->
            VoiceCameraTarget.OBJECT_DETECTION

        text.containsAny("mo ta", "canh", "khung canh", "truoc mat", "xung quanh", "scene", "describe") ->
            VoiceCameraTarget.SCENE_DESCRIPTION

        text.containsAny("ocr", "doc", "doc van ban", "doc chu", "van ban", "text", "read") ->
            VoiceCameraTarget.OCR

        else -> null
    }

    private fun detectOcrMode(text: NormalizedText): OcrMode? = when {
        text.containsAny("chinh xac", "doc chinh xac", "ocr chinh xac", "ocr ky", "accurate") -> OcrMode.ACCURACY
        text.containsAny("nhanh", "doc nhanh", "ocr nhanh", "quick") -> OcrMode.QUICK
        else -> null
    }

    private fun hasCaptureCue(text: NormalizedText): Boolean = text.containsAny(
        "chup",
        "chup anh",
        "quet",
        "quet ngay",
        "luon",
        "ngay",
        "doc luon",
        "nhan dien ngay",
        "xem luon",
        "mo ta luon",
        "capture",
        "scan now"
    )

    private data class NormalizedText(
        val raw: String,
        val folded: String
    ) {
        fun containsAny(vararg phrases: String): Boolean = phrases.any(::containsPhrase)

        private fun containsPhrase(phrase: String): Boolean {
            val normalizedPhrase = phrase.lowercase(Locale.ROOT).trim()
            if (normalizedPhrase.isBlank()) return false
            return Regex("(^|\\s)${Regex.escape(normalizedPhrase)}($|\\s)").containsMatchIn(folded)
        }

        companion object {
            fun from(raw: String): NormalizedText {
                val lower = raw.lowercase(Locale.ROOT)
                val noDiacritics = Normalizer.normalize(lower, Normalizer.Form.NFD)
                    .replace(DIACRITIC_REGEX, "")
                    .replace('đ', 'd')
                    .replace('Đ', 'd')
                val decimalNormalized = noDiacritics.replace(DECIMAL_SEPARATOR_REGEX, "\$1.\$2")
                val folded = decimalNormalized
                    .replace(NON_WORD_OR_DECIMAL_REGEX, " ")
                    .replace(NON_DECIMAL_DOT_REGEX, " ")
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                return NormalizedText(raw = raw, folded = folded)
            }
        }
    }

    private companion object {
        private val DIACRITIC_REGEX = Regex("\\p{Mn}+")
        private val DECIMAL_SEPARATOR_REGEX = Regex("(\\d)[,.](\\d)")
        private val NON_WORD_OR_DECIMAL_REGEX = Regex("[^a-z0-9.]+")
        private val NON_DECIMAL_DOT_REGEX = Regex("(?<!\\d)\\.|\\.(?!\\d)")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val EMERGENCY_DIAL_REGEX = Regex("(?:^|\\s)(?:goi|quay so|mo ban phim goi|mo trinh goi)\\s*(113|114|115|112)(?:\\s|$)")
        private val SPEED_REGEX = Regex("(?:^|\\s)(0[,.]5|0[,.]50|0[,.]75|1|1[,.]0|1[,.]00|1[,.]25|1[,.]5|1[,.]50)(?:\\s|$)")
        private val TTS_SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
        private const val SPEED_MATCH_TOLERANCE = 0.001f
    }
}
