package com.example.eyes.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.scale

class MiDasDepthEstimator(context: Context) {

    private val interpreter = Interpreter(
        FileUtil.loadMappedFile(context, MODEL_PATH),
        Interpreter.Options().apply {
            numThreads = 4
            setUseNNAPI(false)
            setUseXNNPACK(false)
        }
    )

    private val inputShape = interpreter.getInputTensor(0).shape()
    private val outputShape = interpreter.getOutputTensor(0).shape()

    private val inputHeight = inputShape[1]
    private val inputWidth = inputShape[2]
    private val outputHeight = outputShape[1]
    private val outputWidth = outputShape[2]
    private val inputPixels = IntArray(inputWidth * inputHeight)
    private val inputBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
    private val outputBuffer = Array(1) { Array(outputHeight) { Array(outputWidth) { FloatArray(1) } } }
    private val flattenedOutput = FloatArray(outputWidth * outputHeight)

    /**
     * Estimates a depth map for the provided bitmap using the internal MiDaS model.
     *
     * Scales the image to the model input size, runs inference, normalizes the raw depth outputs to the range [0, 1], and returns them at the model's output resolution.
     *
     * @param bitmap Source image to estimate depth from.
     * @return DepthMap containing normalized depth values in row-major order and the model output width and height.
     */
    @Synchronized
    fun estimateDepth(bitmap: Bitmap): DepthMap {
        val scaled = bitmap.scale(inputWidth, inputHeight)
        try {
            val input = bitmapToFloatBuffer(scaled)
            interpreter.run(input, outputBuffer)

            var index = 0
            for (y in 0 until outputHeight) {
                for (x in 0 until outputWidth) {
                    flattenedOutput[index++] = outputBuffer[0][y][x][0]
                }
            }

            val normalized = normalizeDepth(flattenedOutput)
            return DepthMap(values = normalized, width = outputWidth, height = outputHeight)
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    /**
     * Computes the average depth inside a normalized bounding box on the provided depth map.
     *
     * The bounding box coordinates are expected in normalized image space (0.0–1.0) and are
     * clamped to the depth map bounds before sampling. The depth map's `values` are read
     * as a row-major flattened array of size `width * height`.
     *
     * @param depthMap The depth map containing flattened row-major depth values and its dimensions.
     * @param bbox A bounding box in normalized coordinates (left, top, right, bottom) relative to the depth map.
     * @return The average depth value within the clamped bounding box, or `0f` if the box is invalid or contains no pixels.
     */
    fun depthAt(depthMap: DepthMap, bbox: RectF): Float {
        val x1 = (bbox.left * depthMap.width).toInt().coerceIn(0, depthMap.width - 1)
        val y1 = (bbox.top * depthMap.height).toInt().coerceIn(0, depthMap.height - 1)
        val x2 = (bbox.right * depthMap.width).toInt().coerceIn(0, depthMap.width - 1)
        val y2 = (bbox.bottom * depthMap.height).toInt().coerceIn(0, depthMap.height - 1)

        if (x2 < x1 || y2 < y1) return 0f

        var sum = 0f
        var count = 0
        for (y in y1..y2) {
            for (x in x1..x2) {
                sum += depthMap.values[y * depthMap.width + x]
                count++
            }
        }

        return if (count > 0) sum / count else 0f
    }

    /**
     * Normalize a set of depth samples to the range 0..1.
     *
     * @param values Depth values to normalize (preserved order).
     * @return A new FloatArray where each element is scaled to [0,1]. If the input values have
     *     effectively zero range (difference <= 0.0001), returns a new array of zeros of the same size.
     */
    private fun normalizeDepth(values: FloatArray): FloatArray {
        var minValue = Float.MAX_VALUE
        var maxValue = -Float.MAX_VALUE
        values.forEach { value ->
            minValue = min(minValue, value)
            maxValue = max(maxValue, value)
        }

        val range = maxValue - minValue
        if (range <= 0.0001f) {
            return FloatArray(values.size) { 0f }
        }

        return FloatArray(values.size) { index ->
            (values[index] - minValue) / range
        }
    }

    /**
     * Converts the given Bitmap into the estimator's input ByteBuffer containing normalized RGB float values.
     *
     * The buffer contains 3 floats per pixel in row-major order (R, G, B), each normalized to the range [0, 1],
     * and is rewound so it can be read from its start.
     *
     * @return A ByteBuffer with the normalized RGB float pixel data ready for inference.
     */
    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        bitmap.getPixels(inputPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        inputBuffer.rewind()
        inputPixels.forEach { px ->
            inputBuffer.putFloat((px shr 16 and 0xFF) / 255f)
            inputBuffer.putFloat((px shr 8 and 0xFF) / 255f)
            inputBuffer.putFloat((px and 0xFF) / 255f)
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private companion object {
        private const val MODEL_PATH = "models/midas.tflite"
        private const val FLOAT_BYTES = 4
    }
}

data class DepthMap(
    val values: FloatArray,
    val width: Int,
    val height: Int
)
