package com.example.eyes.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.eyes.R
import com.example.eyes.domain.ocr.OcrTextBounds

@Composable
fun OcrGuidanceOverlay(
    bounds: OcrTextBounds?,
    isReady: Boolean,
    modifier: Modifier = Modifier
) {
    val readyDescription = stringResource(R.string.camera_ocr_guidance_ready_description)
    val frameDescription = stringResource(R.string.camera_ocr_guidance_frame_description)
    val overlayDescription = if (isReady) readyDescription else frameDescription

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = overlayDescription
            }
    ) {
        val frameStroke = 2.dp.toPx()
        val guideColor = if (isReady) Color(0xFF66BB6A) else Color(0xFFFFD54F)
        val horizontalMargin = size.width * 0.12f
        val verticalMargin = size.height * 0.18f

        drawRoundRect(
            color = Color.White.copy(alpha = 0.30f),
            topLeft = Offset(horizontalMargin, verticalMargin),
            size = Size(size.width - horizontalMargin * 2f, size.height - verticalMargin * 2f),
            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            style = Stroke(width = frameStroke)
        )

        bounds ?: return@Canvas

        val left = bounds.left * size.width
        val top = bounds.top * size.height
        val right = bounds.right * size.width
        val bottom = bounds.bottom * size.height
        if (right <= left || bottom <= top) return@Canvas

        drawRoundRect(
            color = guideColor,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
