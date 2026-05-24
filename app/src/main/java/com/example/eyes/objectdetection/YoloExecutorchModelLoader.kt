package com.example.eyes.objectdetection

import android.util.Log
import org.pytorch.executorch.Module

class YoloExecutorchModelLoader(
    private val assetCopier: ExecutorchModelAssetCopier
) {

    @Volatile
    private var module: Module? = null

    fun load(): Module {
        module?.let { return it }
        try {
            return synchronized(this) {
                module ?: Module.load(assetCopier.copyModelToFilesDir().absolutePath).also { loadedModule ->
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
