package com.example.eyes.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ObjectDetectionOverlay(
    detections: List<DetectionOverlayItem>,
    modifier: Modifier = Modifier,
    sourceAspectRatio: Float? = null
) {
    val description = detections.joinToString { detection ->
        "${detection.label} ${detection.positionText}"
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    ) {
        val contentWidth: Float
        val contentHeight: Float
        val contentLeft: Float
        val contentTop: Float
        if (sourceAspectRatio != null && sourceAspectRatio > 0f) {
            val canvasAspectRatio = size.width / size.height
            if (canvasAspectRatio > sourceAspectRatio) {
                contentHeight = size.height
                contentWidth = contentHeight * sourceAspectRatio
                contentLeft = (size.width - contentWidth) / 2f
                contentTop = 0f
            } else {
                contentWidth = size.width
                contentHeight = contentWidth / sourceAspectRatio
                contentLeft = 0f
                contentTop = (size.height - contentHeight) / 2f
            }
        } else {
            contentWidth = size.width
            contentHeight = size.height
            contentLeft = 0f
            contentTop = 0f
        }
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 14.sp.toPx()
            isAntiAlias = true
            setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
        }
        detections.forEachIndexed { index, detection ->
            val color = BOX_COLORS[index % BOX_COLORS.size]
            val left = contentLeft + detection.left.coerceIn(0f, 1f) * contentWidth
            val top = contentTop + detection.top.coerceIn(0f, 1f) * contentHeight
            val right = contentLeft + detection.right.coerceIn(0f, 1f) * contentWidth
            val bottom = contentTop + detection.bottom.coerceIn(0f, 1f) * contentHeight
            if (right <= left || bottom <= top) return@forEachIndexed
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    "%s %.0f%%".format(detection.label, detection.confidence * 100f),
                    left,
                    (top - 8.dp.toPx()).coerceAtLeast(20.dp.toPx()),
                    textPaint
                )
            }
        }
    }
}

private val BOX_COLORS = listOf(
    Color(0xFF00E676),
    Color(0xFFFFD54F),
    Color(0xFF40C4FF),
    Color(0xFFFF5252)
)
