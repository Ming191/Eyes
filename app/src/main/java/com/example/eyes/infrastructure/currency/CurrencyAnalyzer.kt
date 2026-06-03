package com.example.eyes.infrastructure.currency

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.example.eyes.infrastructure.camera.toBitmapWithRotation
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList

/**
 * Vietnamese banknote recognition using a single YOLOv8 detector (best.tflite).
 * The model's 9 classes correspond directly to denominations listed in
 * currency_labels.txt, so no separate classification stage is required.
 */
class CurrencyAnalyzer internal constructor(
    private val recognizer: CurrencyRecognizer,
    private val onResult: (label: String, confidence: Float) -> Unit
) {
    private val frameWindow = LinkedList<Map<String, Float>>()

    constructor(context: Context, onResult: (label: String, confidence: Float) -> Unit) : this(
        TensorFlowCurrencyRecognizer(context),
        onResult
    )

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

    internal fun analyzeStreamingFrame(bitmap: Bitmap) {
        try {
            processFrame(bitmap)
        } catch (_: Throwable) {
            onResult(EMPTY_LABEL, 0f)
        }
    }

    fun close() {
        recognizer.close()
    }

    private fun processSingleCapture(bitmap: Bitmap) {
        val result = recognizer.recognize(bitmap)
        if (result != null && result.second >= CONFIDENCE_THRESHOLD) {
            onResult(result.first, result.second)
        } else {
            onResult(EMPTY_LABEL, 0f)
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        val result = recognizer.recognize(bitmap)
        if (result != null && result.second >= CONFIDENCE_THRESHOLD) {
            pushWindow(mapOf(result.first to result.second))
        } else {
            pushWindow(null)
            if (result == null && frameWindow.none { it.isNotEmpty() }) {
                onResult(EMPTY_LABEL, 0f)
                computeStable()
                return
            }
        }

        val (stableLabel, stableConfidence) = computeStable()
        if (stableLabel != null) {
            onResult(stableLabel, stableConfidence)
        } else {
            onResult(EMPTY_LABEL, 0f)
        }
    }

    internal interface CurrencyRecognizer {
        fun recognize(bitmap: Bitmap): Pair<String, Float>?
        fun close()
    }

    private class TensorFlowCurrencyRecognizer(context: Context) : CurrencyRecognizer {
        private val interpreter: Interpreter
        private val labels: List<String>

        private val inputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(inputBitmap)
        private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        private val inputBuffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        init {
            val options = Interpreter.Options().apply { numThreads = 4 }
            interpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_PATH), options)
            labels = FileUtil.loadLabels(context, LABELS_PATH)
        }

        override fun recognize(bitmap: Bitmap): Pair<String, Float>? {
            canvas.drawBitmap(bitmap, null, Rect(0, 0, INPUT_SIZE, INPUT_SIZE), null)
            inputBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            inputBuffer.rewind()
            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((pixel and 0xFF) / 255f)
            }
            inputBuffer.rewind()

            val detectionsCount = interpreter.getOutputTensor(0).shape()[1]
            val output = Array(1) { Array(detectionsCount) { FloatArray(DETECTION_STRIDE) } }
            interpreter.run(inputBuffer, output)

            var bestConfidence = CONFIDENCE_THRESHOLD
            var bestLabel: String? = null
            for (index in 0 until detectionsCount) {
                val detection = output[0][index]
                val confidence = detection[4]
                if (confidence <= bestConfidence) continue
                val classId = detection[5].toInt()
                if (classId !in labels.indices) continue
                bestConfidence = confidence
                bestLabel = labels[classId]
            }
            return bestLabel?.let { it to bestConfidence }
        }

        override fun close() {
            interpreter.close()
        }
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
        if (best.value < CONFIDENCE_THRESHOLD * 0.5f) return null to 0f
        if (bestCount < STABLE_FRAMES) return null to 0f

        val averageConfidence = totalScores.getValue(best.key) / bestCount.toFloat()
        return best.key to averageConfidence
    }

    companion object {
        const val EMPTY_LABEL = ""

        private const val MODEL_PATH = "best.tflite"
        private const val LABELS_PATH = "currency_labels.txt"

        private const val INPUT_SIZE = 640
        private const val DETECTION_STRIDE = 6
        private const val WINDOW_SIZE = 5
        private const val STABLE_FRAMES = 3
        private const val CONFIDENCE_THRESHOLD = 0.70f
    }
}
