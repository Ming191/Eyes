package com.example.eyes.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class YoloDetector(context: Context) : Closeable {

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
    private val closed = AtomicBoolean(false)

    /**
     * Run the YOLO model on the provided bitmap and produce filtered, sorted detections.
     *
     * The bitmap is scaled to the model's input size before inference.
     *
     * @param bitmap The input image to analyze; will be resized to the model's expected dimensions.
     * @param confThreshold Minimum confidence required for a detection to be included.
     * @return A list of detections that meet `confThreshold`, sorted by descending confidence and limited to `MAX_RESULTS`.
     */
    @Synchronized
    fun detect(
        bitmap: Bitmap,
        confThreshold: Float = DEFAULT_CONFIDENCE
    ): List<Detection> {
        if (closed.get()) return emptyList()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val inputBuffer = bitmapToFloatBuffer(scaledBitmap)
        scaledBitmap.recycle()
        val output = Array(1) { Array(outputDetections) { FloatArray(6) } }

        interpreter.run(inputBuffer, output)

        return output[0]
            .asSequence()
            .mapNotNull { row -> row.toDetection(confThreshold) }
            .sortedByDescending { it.confidence }
            .take(MAX_RESULTS)
            .toList()
    }

    @Synchronized
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            interpreter.close()
        }
    }

    /**
     * Convert a model output row into a Detection when confidence and geometry are valid.
     *
     * @receiver A per-row model output expected as six floats in order: `[x1, y1, x2, y2, conf, class]`.
     *           Coordinates may be normalized to `0..1` or provided in pixel space; the function normalizes
     *           coordinates against the model input dimensions and coerces them into `0..1`.
     *           The `class` value is treated as an integer index into the loaded labels.
     * @param confThreshold Minimum confidence required for the row to be considered a detection.
     * @return A populated `Detection` when the row's confidence is at or above `confThreshold` and the
     *         bounding box has positive area; `null` otherwise.
     */
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

        val bbox = BBox(x1, y1, x2, y2)
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

    /**
     * Converts a bitmap into a direct ByteBuffer of normalized RGB float values for model input.
     *
     * The provided `bitmap` must already be scaled to the detector's expected `inputWidth` x `inputHeight`.
     *
     * @param bitmap The source image sized to the model input dimensions.
     * @return A direct, native-order ByteBuffer containing floats in RGB channel order for each pixel,
     *         where each channel value is normalized to the range 0.0..1.0.
     */
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

    /**
     * Maps a normalized horizontal center coordinate to a left/center/right zone.
     *
     * @param centerX The bounding-box center X in normalized coordinates (0.0–1.0).
     * @return `Zone.LEFT` if `centerX < 0.33`, `Zone.RIGHT` if `centerX > 0.66`, otherwise `Zone.CENTER`.
     */
    private fun zoneFromCenterX(centerX: Float): Zone {
        return when {
            centerX < 0.33f -> Zone.LEFT
            centerX > 0.66f -> Zone.RIGHT
            else -> Zone.CENTER
        }
    }

    /**
     * Parse a raw label line into a LabelEntry containing English and Vietnamese text.
     *
     * @param raw A single label line, optionally containing an English and Vietnamese part separated by `|`.
     * @return A LabelEntry where `en` is the first part (trimmed, or empty if missing) and `vi` is the second part trimmed if present and not blank, otherwise the same as `en`.
     */
    private fun parseLabel(raw: String): LabelEntry {
        val parts = raw.split("|", limit = 2)
        val en = parts.firstOrNull()?.trim().orEmpty()
        val vi = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: en
        return LabelEntry(en = en, vi = vi)
    }

    /**
     * Normalize a coordinate value to the range expected by the model.
     *
     * If `value` is greater than 1, it is treated as a pixel coordinate and divided by `dimension` to produce a normalized value; otherwise `value` is returned unchanged. If `dimension` is less than or equal to 0, returns 0.
     *
     * @param value Coordinate value in either normalized form (0..1) or pixel units.
     * @param dimension Size of the corresponding image dimension (width or height) in pixels.
     * @return A normalized coordinate (ratio relative to `dimension`) or `0f` when `dimension <= 0`.
     */
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
