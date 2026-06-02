package com.example.eyes.infrastructure.objectdetection

import android.util.Log
import org.pytorch.executorch.Module
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Tensor

interface YoloExecutorchModel {
    fun forward(input: FloatArray, shape: LongArray): List<YoloExecutorchTensor>
}

data class YoloExecutorchTensor(
    val data: FloatArray,
    val shape: LongArray,
    val dtypeName: String,
    val elementCount: Long
)

class YoloExecutorchModelLoader(
    private val assetCopier: ExecutorchModelAssetCopier,
    private val moduleLoader: (String) -> Module = Module::load
) {

    @Volatile
    private var module: YoloExecutorchModel? = null

    fun load(): YoloExecutorchModel {
        module?.let { return it }
        try {
            return synchronized(this) {
                module ?: ExecuTorchYoloModel(moduleLoader(assetCopier.copyModelToFilesDir().absolutePath)).also { loadedModule ->
                    module = loadedModule
                }
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to load YOLO ExecuTorch model: ${throwable.message}", throwable)
            throw IllegalStateException("Failed to load YOLO ExecuTorch model", throwable)
        }
    }

    private companion object {
        private const val TAG = "YoloExecutorchModelLoader"
    }
}

private class ExecuTorchYoloModel(
    private val module: Module
) : YoloExecutorchModel {
    override fun forward(input: FloatArray, shape: LongArray): List<YoloExecutorchTensor> {
        val inputTensor = Tensor.fromBlob(input, shape)
        return module.forward(EValue.from(inputTensor)).map { output ->
            val tensor = output.toTensor()
            YoloExecutorchTensor(
                data = tensor.getDataAsFloatArray(),
                shape = tensor.shape(),
                dtypeName = tensor.dtype().name,
                elementCount = tensor.numel()
            )
        }
    }
}
