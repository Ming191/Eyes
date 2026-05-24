package com.example.eyes.objectdetection

import org.pytorch.executorch.Module

class YoloExecutorchModelLoader(
    private val assetCopier: ExecutorchModelAssetCopier
) {

    @Volatile
    private var module: Module? = null

    fun load(): Module {
        module?.let { return it }
        return synchronized(this) {
            module ?: Module.load(assetCopier.copyModelToFilesDir().absolutePath).also { module = it }
        }
    }
}
