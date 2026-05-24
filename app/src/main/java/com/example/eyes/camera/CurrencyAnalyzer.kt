package com.example.eyes.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList

/**
 * Vietnamese banknote recognition with 2 stages:
 * 1) best.tflite (YOLO) detects the banknote region.
 * 2) EfficientNet_float32.tflite classifies denomination from the cropped region.
 */
class CurrencyAnalyzer(
    context: Context,
    private val onResult: (label: String, confidence: Float) -> Unit
) {
    private val yoloInterpreter: Interpreter
    private val classifierInterpreter: Interpreter
    private val labels: List<String>

    private val yoloBitmap = Bitmap.createBitmap(YOLO_INPUT_SIZE, YOLO_INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val classifierBitmap = Bitmap.createBitmap(CLASSIFIER_INPUT_SIZE, CLASSIFIER_INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val yoloCanvas = Canvas(yoloBitmap)
    private val classifierCanvas = Canvas(classifierBitmap)

    private val yoloPixels = IntArray(YOLO_INPUT_SIZE * YOLO_INPUT_SIZE)
    private val classifierPixels = IntArray(CLASSIFIER_INPUT_SIZE * CLASSIFIER_INPUT_SIZE)

    private val yoloInputBuffer = ByteBuffer
        .allocateDirect(YOLO_INPUT_SIZE * YOLO_INPUT_SIZE * 3 * 4)
        .order(ByteOrder.nativeOrder())
    private val classifierInputBuffer = ByteBuffer
        .allocateDirect(CLASSIFIER_INPUT_SIZE * CLASSIFIER_INPUT_SIZE * 3 * 4)
        .order(ByteOrder.nativeOrder())

    private val frameWindow = LinkedList<Map<String, Float>>()

    init {
        val options = Interpreter.Options().apply { numThreads = 4 }
        yoloInterpreter = Interpreter(FileUtil.loadMappedFile(context, YOLO_MODEL_PATH), options)
        classifierInterpreter = Interpreter(FileUtil.loadMappedFile(context, CLASSIFIER_MODEL_PATH), options)
        labels = FileUtil.loadLabels(context, LABELS_PATH)
    }

    fun analyze(imageProxy: ImageProxy) {
        var bitmap: Bitmap? = null
        try {
            bitmap = imageProxy.toBitmapWithRotation()
            processFrame(bitmap)
        } catch (_: Throwable) {
            onResult(EMPTY_LABEL, 0f)
        } finally {
            imageProxy.close()
            bitmap?.recycle()
        }
    }

    fun analyze(bitmap: Bitmap) {
        try {
            processSingleCapture(bitmap)
        } catch (_: Throwable) {
            onResult(EMPTY_LABEL, 0f)
        }
    }

    fun resetBuffer() {
        frameWindow.clear()
    }

    fun close() {
        yoloInterpreter.close()
        classifierInterpreter.close()
    }

    private fun processSingleCapture(bitmap: Bitmap) {
        val box = detectWithYolo(bitmap) ?: run {
            onResult(EMPTY_LABEL, 0f)
            return
        }

        val cropped = cropBitmap(bitmap, box) ?: run {
            onResult(EMPTY_LABEL, 0f)
            return
        }

        val (label, confidence) = classifyWithEfficientNet(cropped)
        cropped.recycle()

        if (confidence >= CLASSIFIER_CONFIDENCE_THRESHOLD) {
            onResult(label, confidence)
        } else {
            onResult(EMPTY_LABEL, 0f)
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        val box = detectWithYolo(bitmap)
        if (box == null) {
            pushWindow(null)
            val hadAnyResult = frameWindow.any { it.isNotEmpty() }
            if (!hadAnyResult) {
                onResult(EMPTY_LABEL, 0f)
            }
            computeStable()
            return
        }

        val cropped = cropBitmap(bitmap, box)
        if (cropped == null) {
            pushWindow(null)
            onResult(EMPTY_LABEL, 0f)
            return
        }

        val (label, confidence) = classifyWithEfficientNet(cropped)
        cropped.recycle()

        if (confidence >= CLASSIFIER_CONFIDENCE_THRESHOLD) {
            pushWindow(mapOf(label to confidence))
        } else {
            pushWindow(null)
        }

        val (stableLabel, stableConfidence) = computeStable()
        if (stableLabel != null) {
            onResult(stableLabel, stableConfidence)
        } else {
            onResult(EMPTY_LABEL, 0f)
        }
    }

    private data class BBox(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    )

    private fun detectWithYolo(bitmap: Bitmap): BBox? {
        yoloCanvas.drawBitmap(bitmap, null, Rect(0, 0, YOLO_INPUT_SIZE, YOLO_INPUT_SIZE), null)
        yoloBitmap.getPixels(yoloPixels, 0, YOLO_INPUT_SIZE, 0, 0, YOLO_INPUT_SIZE, YOLO_INPUT_SIZE)

        yoloInputBuffer.rewind()
        for (pixel in yoloPixels) {
            yoloInputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            yoloInputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            yoloInputBuffer.putFloat((pixel and 0xFF) / 255f)
        }
        yoloInputBuffer.rewind()

        val detectionsCount = yoloInterpreter.getOutputTensor(0).shape()[1]
        val output = Array(1) { Array(detectionsCount) { FloatArray(6) } }
        yoloInterpreter.run(yoloInputBuffer, output)

        var bestConfidence = YOLO_CONFIDENCE_THRESHOLD
        var bestBox: BBox? = null
        for (index in 0 until detectionsCount) {
            val detection = output[0][index]
            val confidence = detection[4]
            if (confidence > bestConfidence) {
                bestConfidence = confidence
                bestBox = BBox(
                    x1 = detection[0].coerceIn(0f, 1f),
                    y1 = detection[1].coerceIn(0f, 1f),
                    x2 = detection[2].coerceIn(0f, 1f),
                    y2 = detection[3].coerceIn(0f, 1f)
                )
            }
        }
        return bestBox
    }

    private fun classifyWithEfficientNet(cropped: Bitmap): Pair<String, Float> {
        classifierCanvas.drawBitmap(
            cropped,
            null,
            Rect(0, 0, CLASSIFIER_INPUT_SIZE, CLASSIFIER_INPUT_SIZE),
            null
        )

        classifierBitmap.getPixels(
            classifierPixels,
            0,
            CLASSIFIER_INPUT_SIZE,
            0,
            0,
            CLASSIFIER_INPUT_SIZE,
            CLASSIFIER_INPUT_SIZE
        )

        classifierInputBuffer.rewind()
        for (pixel in classifierPixels) {
            classifierInputBuffer.putFloat((((pixel shr 16) and 0xFF) / 255f - 0.485f) / 0.229f)
            classifierInputBuffer.putFloat((((pixel shr 8) and 0xFF) / 255f - 0.456f) / 0.224f)
            classifierInputBuffer.putFloat(((pixel and 0xFF) / 255f - 0.406f) / 0.225f)
        }
        classifierInputBuffer.rewind()

        val output = Array(1) { FloatArray(labels.size) }
        classifierInterpreter.run(classifierInputBuffer, output)

        val scores = output[0]
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        return labels[maxIndex] to scores[maxIndex]
    }

    private fun cropBitmap(source: Bitmap, box: BBox): Bitmap? {
        val x1 = (box.x1 * source.width).toInt().coerceIn(0, source.width)
        val y1 = (box.y1 * source.height).toInt().coerceIn(0, source.height)
        val x2 = (box.x2 * source.width).toInt().coerceIn(0, source.width)
        val y2 = (box.y2 * source.height).toInt().coerceIn(0, source.height)
        val width = x2 - x1
        val height = y2 - y1
        if (width <= 0 || height <= 0) return null
        return Bitmap.createBitmap(source, x1, y1, width, height)
    }

    private fun pushWindow(scores: Map<String, Float>?) {
        frameWindow.addLast(scores ?: emptyMap())
        if (frameWindow.size > WINDOW_SIZE) {
            frameWindow.removeFirst()
        }
    }

    private fun computeStable(): Pair<String?, Float> {
        if (frameWindow.size < STABLE_FRAMES) return null to 0f

        val windowSize = frameWindow.size.toFloat()
        val totalScores = mutableMapOf<String, Float>()
        val appearanceCount = mutableMapOf<String, Int>()

        for (frame in frameWindow) {
            for ((label, score) in frame) {
                totalScores[label] = totalScores.getOrDefault(label, 0f) + score
                appearanceCount[label] = appearanceCount.getOrDefault(label, 0) + 1
            }
        }

        val weightedScores = totalScores.mapValues { (label, summedScore) ->
            val ratio = appearanceCount.getOrDefault(label, 0) / windowSize
            (summedScore / windowSize) * ratio
        }

        val best = weightedScores.maxByOrNull { it.value } ?: return null to 0f
        val bestCount = appearanceCount.getOrDefault(best.key, 0)
        if (best.value < CLASSIFIER_CONFIDENCE_THRESHOLD * 0.5f) return null to 0f
        if (bestCount < STABLE_FRAMES) return null to 0f

        val averageConfidence = totalScores.getValue(best.key) / bestCount.toFloat()
        return best.key to averageConfidence
    }

    companion object {
        const val EMPTY_LABEL = ""

        private const val YOLO_MODEL_PATH = "best.tflite"
        private const val CLASSIFIER_MODEL_PATH = "EfficientNet_float32.tflite"
        private const val LABELS_PATH = "currency_labels.txt"

        private const val YOLO_INPUT_SIZE = 640
        private const val CLASSIFIER_INPUT_SIZE = 224
        private const val WINDOW_SIZE = 5
        private const val STABLE_FRAMES = 3
        private const val YOLO_CONFIDENCE_THRESHOLD = 0.40f
        private const val CLASSIFIER_CONFIDENCE_THRESHOLD = 0.70f
    }
}
