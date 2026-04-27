package com.example.eyes.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YoloDetector(context: Context) {

    private val interpreter = Interpreter(
        FileUtil.loadMappedFile(context, MODEL_PATH),
        Interpreter.Options().apply {
            numThreads = 4
            setUseNNAPI(false)
        }
    )

    private val labels = FileUtil.loadLabels(context, LABELS_PATH).map { parseLabel(it) }

    private val inputShape = interpreter.getInputTensor(0).shape()
    private val outputShape = interpreter.getOutputTensor(0).shape()

    private val inputHeight = inputShape[1]
    private val inputWidth = inputShape[2]
    private val outputDetections = outputShape[1]

    fun detect(
        bitmap: Bitmap,
        confThreshold: Float = DEFAULT_CONFIDENCE
    ): List<Detection> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val inputBuffer = bitmapToFloatBuffer(scaledBitmap)
        val output = Array(1) { Array(outputDetections) { FloatArray(6) } }

        interpreter.run(inputBuffer, output)

        return output[0]
            .asSequence()
            .mapNotNull { row -> row.toDetection(confThreshold) }
            .sortedByDescending { it.confidence }
            .take(MAX_RESULTS)
            .toList()
    }

    private fun FloatArray.toDetection(confThreshold: Float): Detection? {
        val confidence = this[4]
        if (confidence < confThreshold) return null

        val classIndex = this[5].toInt()
        val (labelEn, labelVi) = labels.getOrElse(classIndex) { LabelEntry("unknown", "vật thể") }

        // YOLO26 FP16 TFLite export returns [x1, y1, x2, y2, conf, class] normalized to [0..1].
        // Keep dimension fallback so this still works if a future export returns pixel-space boxes.
        val x1 = normalizeCoordinate(this[0], inputWidth.toFloat()).coerceIn(0f, 1f)
        val y1 = normalizeCoordinate(this[1], inputHeight.toFloat()).coerceIn(0f, 1f)
        val x2 = normalizeCoordinate(this[2], inputWidth.toFloat()).coerceIn(0f, 1f)
        val y2 = normalizeCoordinate(this[3], inputHeight.toFloat()).coerceIn(0f, 1f)

        if (x2 <= x1 || y2 <= y1) return null

        val bbox = RectF(x1, y1, x2, y2)
        val zone = zoneFromCenterX((x1 + x2) / 2f)

        val heightScore = (y2 - y1).coerceIn(0f, 1f)
        val areaScore = ((x2 - x1) * (y2 - y1)).coerceIn(0f, 1f)
        val bboxDepthScore = (heightScore * 0.7f) + (areaScore * 0.3f)

        return Detection(
            labelEn = labelEn,
            labelVi = labelVi,
            bbox = bbox,
            confidence = confidence,
            zone = zone,
            bboxDepthScore = bboxDepthScore
        )
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        val buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())

        pixels.forEach { px ->
            buffer.putFloat((px shr 16 and 0xFF) / 255f)
            buffer.putFloat((px shr 8 and 0xFF) / 255f)
            buffer.putFloat((px and 0xFF) / 255f)
        }

        buffer.rewind()
        return buffer
    }

    private fun zoneFromCenterX(centerX: Float): Zone {
        return when {
            centerX < 0.33f -> Zone.LEFT
            centerX > 0.66f -> Zone.RIGHT
            else -> Zone.CENTER
        }
    }

    private fun parseLabel(raw: String): LabelEntry {
        val parts = raw.split("|", limit = 2)
        val en = parts.firstOrNull()?.trim().orEmpty()
        val vi = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: en
        return LabelEntry(en = en, vi = vi)
    }

    private fun normalizeCoordinate(value: Float, dimension: Float): Float {
        if (dimension <= 0f) return 0f
        return if (value > 1f) value / dimension else value
    }

    private data class LabelEntry(
        val en: String,
        val vi: String
    )

    private companion object {
        private const val MODEL_PATH = "models/yolo26m_float16.tflite"
        private const val LABELS_PATH = "models/labels_vi.txt"
        private const val FLOAT_BYTES = 4
        private const val DEFAULT_CONFIDENCE = 0.4f
        private const val MAX_RESULTS = 20
    }
}
