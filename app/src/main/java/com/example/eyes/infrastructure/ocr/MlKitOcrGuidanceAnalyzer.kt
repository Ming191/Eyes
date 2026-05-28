package com.example.eyes.infrastructure.ocr

import android.graphics.Bitmap
import android.graphics.Color
import com.example.eyes.domain.ocr.OcrGuidanceFrame
import com.example.eyes.domain.ocr.OcrTextBounds
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrGuidanceAnalyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(bitmap: Bitmap): OcrGuidanceFrame =
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    val bounds = text.textBlocks
                        .mapNotNull { block ->
                            block.boundingBox?.toNormalizedBounds(
                                bitmap.width,
                                bitmap.height
                            )
                        }
                        .reduceOrNull { acc, blockBounds -> acc.union(blockBounds) }
                    val lineCount = text.textBlocks.sumOf { it.lines.size }
                    continuation.resume(
                        OcrGuidanceFrame(
                            textBounds = bounds,
                            lineCount = lineCount,
                            textLength = text.text.length,
                            luminance = bitmap.averageLuminance()
                        )
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    fun close() {
        recognizer.close()
    }

    private fun android.graphics.Rect.toNormalizedBounds(width: Int, height: Int): OcrTextBounds {
        return OcrTextBounds(
            left = (left.toFloat() / width).coerceIn(0f, 1f),
            top = (top.toFloat() / height).coerceIn(0f, 1f),
            right = (right.toFloat() / width).coerceIn(0f, 1f),
            bottom = (bottom.toFloat() / height).coerceIn(0f, 1f)
        )
    }

    private fun Bitmap.averageLuminance(): Float {
        val stepX = (width / SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (height / SAMPLE_GRID).coerceAtLeast(1)
        var total = 0f
        var count = 0

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                total += (
                    Color.red(pixel) * 0.299f +
                        Color.green(pixel) * 0.587f +
                        Color.blue(pixel) * 0.114f
                    ) / 255f
                count++
                x += stepX
            }
            y += stepY
        }

        return if (count == 0) 0f else total / count
    }

    private companion object {
        private const val SAMPLE_GRID = 32
    }
}
