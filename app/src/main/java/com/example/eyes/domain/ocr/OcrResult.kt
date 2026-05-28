package com.example.eyes.domain.ocr

/**
 * Kết quả nhận dạng văn bản từ OCR.
 *
 * @param fullText Toàn bộ văn bản đã join, dùng cho similarity check.
 * @param sentences Danh sách câu đã tách cho Document mode.
 */
data class OcrResult(
    val fullText: String,
    val sentences: List<String>,
) {
    val isEmpty: Boolean get() = fullText.isBlank()

    companion object {
        val EMPTY = OcrResult(fullText = "", sentences = emptyList())
    }
}
