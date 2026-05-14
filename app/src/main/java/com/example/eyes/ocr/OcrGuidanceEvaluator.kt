package com.example.eyes.ocr

object OcrGuidanceEvaluator {
    private const val MIN_TEXT_COVERAGE = 0.06f
    private const val MAX_TEXT_COVERAGE = 0.92f
    private const val SEVERE_EDGE_MARGIN = 0.01f
    private const val MAX_CENTER_OFFSET = 0.24f
    private const val MIN_LUMINANCE = 0.15f
    private const val MAX_LUMINANCE = 0.95f
    private const val REQUIRED_STABLE_FRAMES = 2

    fun evaluate(frame: OcrGuidanceFrame, stableFrameCount: Int): OcrGuidanceEvaluation {
        val bounds = frame.textBounds
            ?: return OcrGuidanceEvaluation(
                status = OcrGuidanceStatus.SEARCHING,
                message = "Chưa thấy văn bản. Hãy hướng camera vào vùng chữ.",
                isReadyToCapture = false,
                textBounds = null
            )

        if (frame.luminance < MIN_LUMINANCE) {
            return frame.warning(
                status = OcrGuidanceStatus.TOO_DARK,
                message = "Ảnh hơi tối. Hãy đưa văn bản ra nơi sáng hơn."
            )
        }

        if (frame.luminance > MAX_LUMINANCE) {
            return frame.warning(
                status = OcrGuidanceStatus.TOO_BRIGHT,
                message = "Ảnh quá sáng. Hãy tránh ánh sáng chiếu trực tiếp vào giấy."
            )
        }

        if (bounds.area < MIN_TEXT_COVERAGE) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_CLOSER,
                message = "Văn bản còn nhỏ. Hãy đưa camera lại gần hơn."
            )
        }

        if (bounds.area > MAX_TEXT_COVERAGE) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_BACK,
                message = "Văn bản quá gần. Hãy đưa camera ra xa một chút."
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
                message = "Văn bản sát mép khung. Hãy lấy rộng ra để không mất chữ."
            )
        }

        val offsetX = bounds.centerX - 0.5f
        if (offsetX < -MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_LEFT,
                message = "Văn bản lệch trái. Hãy hướng camera sang trái."
            )
        }
        if (offsetX > MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_RIGHT,
                message = "Văn bản lệch phải. Hãy hướng camera sang phải."
            )
        }

        val offsetY = bounds.centerY - 0.5f
        if (offsetY < -MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_UP,
                message = "Văn bản lệch lên trên. Hãy nâng camera lên một chút."
            )
        }
        if (offsetY > MAX_CENTER_OFFSET) {
            return frame.warning(
                status = OcrGuidanceStatus.MOVE_DOWN,
                message = "Văn bản lệch xuống dưới. Hãy hạ camera xuống một chút."
            )
        }

        return if (stableFrameCount >= REQUIRED_STABLE_FRAMES) {
            OcrGuidanceEvaluation(
                status = OcrGuidanceStatus.READY,
                message = "Văn bản đã nằm trong khung, sẵn sàng chụp.",
                isReadyToCapture = true,
                textBounds = bounds
            )
        } else {
            frame.warning(
                status = OcrGuidanceStatus.HOLD_STEADY,
                message = "Đã thấy văn bản. Hãy giữ camera ổn định."
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
