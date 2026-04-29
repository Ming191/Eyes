package com.example.eyes.ocr

/**
 * Post-process kết quả OCR đã chuẩn hóa thành chuỗi text.
 */
object OcrPostProcessor {

    private val noiseTokens = listOf(
        "|",
        "•",
        "~",
        "_"
    )

    private val correctionRules = mapOf(
        "ko" to "không",
        "k" to "không",
        "dc" to "được",
        "mk" to "mình"
    )

    fun process(rawText: String, language: OcrLanguage = OcrLanguage.AUTO): OcrResult {
        val normalizedText = normalizeText(rawText)
        val resolvedLanguage = resolveLanguage(normalizedText, language)
        val correctedText = if (resolvedLanguage == OcrLanguage.VI) {
            applyLightCorrections(normalizedText)
        } else {
            normalizedText
        }
        val sentences = splitToSentences(correctedText)
        return OcrResult(fullText = correctedText, sentences = sentences)
    }

    fun normalizeText(text: String): String {
        return text
            .replace(Regex("\u00A0"), " ")
            .replace(Regex("\\r\\n|\\r"), "\n")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\\n[ \\t]+"), "\n")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[“”]"), "\"")
            .replace(Regex("[‘’]"), "'")
            .trim()
    }

    fun splitToSentences(text: String): List<String> =
        text
            .split(Regex("[.!?。…]+|\\n+"))
            .map { it.trim().trimEnd(',', ';', ':') }
            .filter { sentence ->
                sentence.isNotBlank() && sentence.split(Regex("\\s+")).size >= 2
            }

    fun similarityRatio(a: String, b: String): Float {
        if (a.isBlank() && b.isBlank()) return 1f
        if (a.isBlank() || b.isBlank()) return 0f
        val maxLen = maxOf(a.length, b.length).toFloat()
        val distance = levenshtein(a, b)
        return 1f - (distance / maxLen)
    }

    fun applyLightCorrections(text: String): String {
        if (text.isBlank()) return text
        var corrected = text
        noiseTokens.forEach { token ->
            corrected = corrected.replace(token, " ")
        }
        correctionRules.forEach { (from, to) ->
            corrected = corrected.replace(Regex("\\b${Regex.escape(from)}\\b", RegexOption.IGNORE_CASE), to)
        }
        return corrected.replace(Regex("\\s+"), " ").trim()
    }

    private fun resolveLanguage(text: String, language: OcrLanguage): OcrLanguage {
        return when (language) {
            OcrLanguage.AUTO -> if (VI_DIACRITIC_REGEX.containsMatchIn(text)) OcrLanguage.VI else OcrLanguage.EN
            else -> language
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    private val VI_DIACRITIC_REGEX = Regex(
        "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
        RegexOption.IGNORE_CASE
    )
}
