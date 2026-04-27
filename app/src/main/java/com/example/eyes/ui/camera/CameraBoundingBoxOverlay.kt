package com.example.eyes.ui.camera

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraBoundingBoxOverlay(
    boxes: List<BoundingBoxUi>,
    modifier: Modifier = Modifier
) {
    val summary = remember(boxes) {
        if (boxes.isEmpty()) {
            "Lớp phủ vật thể chưa có kết quả"
        } else {
            val descriptions = boxes
                .take(3).joinToString(", ") { box -> "${box.labelVi} ${box.zoneLabel}" }
            "Lớp phủ vật thể: $descriptions"
        }
    }

    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 13.sp.value * 3f
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }

    val boxColors = remember {
        mapOf(
            "bên trái" to Color(0xFF64B5F6),
            "chính giữa" to Color(0xFFFF7043),
            "bên phải" to Color(0xFF81C784)
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = summary }
    ) {
        val strokeWidth = 3.dp.toPx()
        val labelPadding = 6.dp.toPx()
        val labelHeight = 24.dp.toPx()

        boxes.forEach { box ->
            val left = (box.left * size.width).coerceIn(0f, size.width)
            val top = (box.top * size.height).coerceIn(0f, size.height)
            val right = (box.right * size.width).coerceIn(0f, size.width)
            val bottom = (box.bottom * size.height).coerceIn(0f, size.height)

            if (right <= left || bottom <= top) return@forEach

            val color = boxColors[box.zoneLabel] ?: Color(0xFFFFD54F)

            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val label = "${box.labelVi} ${(box.confidence * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val backgroundTop = (top - labelHeight).coerceAtLeast(0f)
            val backgroundLeft = left
            val backgroundWidth = (textWidth + labelPadding * 2f)
                .coerceAtMost(size.width - backgroundLeft)

            drawRoundRect(
                color = color.copy(alpha = 0.90f),
                topLeft = Offset(backgroundLeft, backgroundTop),
                size = Size(backgroundWidth, labelHeight),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            drawContext.canvas.nativeCanvas.drawText(
                label,
                backgroundLeft + labelPadding,
                backgroundTop + labelHeight - 7.dp.toPx(),
                textPaint
            )
        }
    }
}
