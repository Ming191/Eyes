package com.example.eyes.objectdetection

import android.content.Context
import java.io.File

class ExecutorchModelAssetCopier(
    private val context: Context
) {

    fun copyModelToFilesDir(
        assetPath: String = DEFAULT_MODEL_ASSET_PATH,
        fileName: String = DEFAULT_MODEL_FILE_NAME
    ): File {
        val targetDir = File(context.filesDir, MODEL_DIR_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, fileName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile
        }

        context.assets.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    companion object {
        const val MODEL_DIR_NAME = "models"
        const val DEFAULT_MODEL_ASSET_PATH = "models/yolo26n.pte"
        const val DEFAULT_MODEL_FILE_NAME = "yolo26n.pte"
    }
}
