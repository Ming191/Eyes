package com.example.eyes.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp

class CurrencyAnalyzer(
    context: Context,
    private val onResult: (String) -> Unit
) {
    private var interpreter: Interpreter? = null

    private val labels = listOf(
        "000000", "000200", "000500", "001000", "002000", "005000",
        "010000", "020000", "050000", "100000", "200000", "500000"
    )

    private val outputBuffer = Array(1) { FloatArray(12) }
    private var cachedProcessor: ImageProcessor? = null
    private var lastInputSize: Pair<Int, Int>? = null

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "currency_model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(1)
                setUseXNNPACK(false)
            }
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {
            throw RuntimeException("Mô hình không tương thích: ${e.localizedMessage}")
        }
    }

    fun analyze(imageProxy: ImageProxy) {
        var bitmap: Bitmap? = null
        try {
            val currentInterpreter = interpreter ?: return
            bitmap = imageProxy.toBitmapWithRotation() ?: return

            if (lastInputSize?.first != bitmap.width || lastInputSize?.second != bitmap.height) {
                val size = minOf(bitmap.width, bitmap.height)
                cachedProcessor = ImageProcessor.Builder()
                    .add(ResizeWithCropOrPadOp(size, size))
                    .add(ResizeOp(144, 144, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(0f, 1f / 255f))
                    .build()
                lastInputSize = Pair(bitmap.width, bitmap.height)
            }

            // ✅ Tạo mới TensorImage mỗi frame
            val tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
            tensorImage.load(bitmap)
            val processedImage = cachedProcessor!!.process(tensorImage)

            // ✅ Reset buffer trước khi run
            outputBuffer[0].fill(0f)
            currentInterpreter.run(processedImage.buffer, outputBuffer)

            val scores = outputBuffer[0]
            val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
            val maxScore = scores[maxIdx]

            if (maxIdx != 0 && maxScore > 0.70f) {
                onResult(labels[maxIdx])
            } else {
                onResult("000000")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            onResult("000000")
        } finally {
            imageProxy.close()
            bitmap?.recycle()
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}