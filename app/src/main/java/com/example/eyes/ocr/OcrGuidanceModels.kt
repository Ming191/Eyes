package com.example.eyes.ocr

import androidx.compose.runtime.Immutable

@Immutable
data class OcrTextBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun union(other: OcrTextBounds): OcrTextBounds = OcrTextBounds(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom)
    )
}

@Immutable
data class OcrGuidanceFrame(
    val textBounds: OcrTextBounds?,
    val lineCount: Int,
    val textLength: Int,
    val luminance: Float
)

@Immutable
enum class OcrGuidanceStatus {
    SEARCHING,
    MOVE_CLOSER,
    MOVE_BACK,
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN,
    TEXT_CLIPPED,
    TOO_DARK,
    TOO_BRIGHT,
    HOLD_STEADY,
    READY
}

@Immutable
data class OcrGuidanceEvaluation(
    val status: OcrGuidanceStatus,
    val message: String,
    val isReadyToCapture: Boolean,
    val textBounds: OcrTextBounds?
)
