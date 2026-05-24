package com.example.eyes.ocr

object OcrGuidanceEvaluator {
    private const val MIN_TEXT_COVERAGE = 0.06f
    private const val MAX_TEXT_COVERAGE = 0.92f
    private const val SEVERE_EDGE_MARGIN = 0.01f
    private const val MAX_CENTER_OFFSET = 0.24f
    private const val MIN_LUMINANCE = 0.15f
    private const val MAX_LUMINANCE = 0.95f
    private const val REQUIRED_STABLE_FRAMES = 2

    fun evaluate(
        frame: OcrGuidanceFrame,
        stableFrameCount: Int,
        text: OcrGuidanceText = OcrGuidanceText()
    ): OcrGuidanceEvaluation {
        val bounds = frame.textBounds
            ?: return OcrGuidanceEvaluation(
                status = OcrGuidanceStatus.SEARCHING,
                message = text.searching,
                isReadyToCapture = false,
                textBounds = null
            )

        if (frame.luminance < MIN_LUMINANCE) {
            return frame.warning(
                status = OcrGuidanceStatus.TOO_DARK,
                message = text.tooDark
            )
        }

        if (frame.luminance > MAX_LUMINANCE) {
            return frame.warning(
                status = OcrGuidanceStatus.TOO_BRIGHT,
                message = text.tooBright
            )
        }

        if (bounds.area < MIN_TEXT_COVERAGE) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_CLOSER,
                message = text.moveCloser
            )
        }

        if (bounds.area > MAX_TEXT_COVERAGE) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_BACK,
                message = text.moveBack
            )
        }

        // Only warn clipped when text is very close to frame border.
        if (
            bounds.left < SEVERE_EDGE_MARGIN ||
            bounds.top < SEVERE_EDGE_MARGIN ||
            bounds.right > 1f - SEVERE_EDGE_MARGIN ||
            bounds.bottom > 1f - SEVERE_EDGE_MARGIN
        ) {
            return frame.warning(
                status = OcrGuidanceStatus.TEXT_CLIPPED,
                message = text.textClipped
            )
        }

        val offsetX = bounds.centerX - 0.5f
        if (offsetX < -MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_LEFT,
                message = text.moveLeft
            )
        }
        if (offsetX > MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_RIGHT,
                message = text.moveRight
            )
        }

        val offsetY = bounds.centerY - 0.5f
        if (offsetY < -MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_UP,
                message = text.moveUp
            )
        }
        if (offsetY > MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_DOWN,
                message = text.moveDown
            )
        }

        return if (stableFrameCount >= REQUIRED_STABLE_FRAMES) {
            OcrGuidanceEvaluation(
                status = OcrGuidanceStatus.READY,
                message = text.ready,
                isReadyToCapture = true,
                textBounds = bounds
            )
        } else {
            frame.warning(
                status = OcrGuidanceStatus.HOLD_STEADY,
                message = text.holdSteady
            )
        }
    }

    private fun OcrGuidanceFrame.warning(
        status: OcrGuidanceStatus,
        message: String
    ): OcrGuidanceEvaluation = OcrGuidanceEvaluation(
        status = status,
        message = message,
        isReadyToCapture = false,
        textBounds = textBounds
    )

}

data class OcrGuidanceText(
    val searching: String = "",
    val tooDark: String = "",
    val tooBright: String = "",
    val moveCloser: String = "",
    val moveBack: String = "",
    val textClipped: String = "",
    val moveLeft: String = "",
    val moveRight: String = "",
    val moveUp: String = "",
    val moveDown: String = "",
    val ready: String = "",
    val holdSteady: String = ""
)
