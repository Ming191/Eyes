package com.example.eyes.objectdetection

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Tensor

class YoloExecutorchDetector(
    private val modelLoader: YoloExecutorchModelLoader,
    private val inputSize: Int = DEFAULT_INPUT_SIZE,
    private val preprocessor: YoloPreprocessor = YoloPreprocessor(inputSize),
    private val postprocessor: YoloPostprocessor = YoloPostprocessor()
) : ObjectDetector {

    suspend fun inspectOutputShape(): List<YoloOutputInfo> = withContext(Dispatchers.Default) {
        val input = FloatArray(1 * CHANNELS * inputSize * inputSize)
        val inputTensor = Tensor.fromBlob(
            input,
            longArrayOf(1L, CHANNELS.toLong(), inputSize.toLong(), inputSize.toLong())
        )
        val outputs = modelLoader.load().forward(EValue.from(inputTensor))
        outputs.mapIndexed { index, output ->
            val tensor = output.toTensor()
            YoloOutputInfo(
                index = index,
                shape = tensor.shape().toList(),
                dtype = tensor.dtype().name,
                elementCount = tensor.numel()
            )
        }.also { outputInfo ->
            Log.i(TAG, "YOLO ExecuTorch output info: $outputInfo")
        }
    }

    override suspend fun detect(bitmap: Bitmap): List<Detection> {
        return withContext(Dispatchers.Default) {
            val input = preprocessor.preprocess(bitmap)
            val inputTensor = Tensor.fromBlob(
                input,
                longArrayOf(1L, CHANNELS.toLong(), inputSize.toLong(), inputSize.toLong())
            )
            val output = modelLoader.load().forward(EValue.from(inputTensor))[0].toTensor().getDataAsFloatArray()
            postprocessor.postprocess(
                output = output,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height
            )
        }
    }

    companion object {
        private const val TAG = "YoloExecutorchDetector"
        private const val CHANNELS = 3
        const val DEFAULT_INPUT_SIZE = 320
    }
}

data class YoloOutputInfo(
    val index: Int,
    val shape: List<Long>,
    val dtype: String,
    val elementCount: Long
)
