package com.example.eyes.ocr

/**
 * Post-process kết quả OCR đã chuẩn hóa thành chuỗi text.
 */
object OcrPostProcessor {

    fun process(rawText: String): OcrResult {
        val normalizedText = rawText.trim()
        val sentences = splitToSentences(normalizedText)
        return OcrResult(fullText = normalizedText, sentences = sentences)
    }

    fun splitToSentences(text: String): List<String> =
        text
            .split(Regex("[.!?。…]+"))
            .map { it.trim() }
            .filter { sentence -> sentence.split(Regex("\\s+")).size >= 2 }

    fun similarityRatio(a: String, b: String): Float {
        if (a.isBlank() && b.isBlank()) return 1f
        if (a.isBlank() || b.isBlank()) return 0f
        val maxLen = maxOf(a.length, b.length).toFloat()
        val distance = levenshtein(a, b)
        return 1f - (distance / maxLen)
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
}
