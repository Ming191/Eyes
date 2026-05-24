package com.example.eyes.objectdetection

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class ExecutorchModelAssetCopier(
    private val context: Context
) {

    fun copyModelToFilesDir(
        assetPath: String = DEFAULT_MODEL_ASSET_PATH,
        fileName: String = DEFAULT_MODEL_FILE_NAME
    ): File {
        try {
            val targetDir = File(context.filesDir, MODEL_DIR_NAME)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val targetFile = File(targetDir, fileName)
            val assetSize = context.assets.open(assetPath).use { it.available().toLong() }
            if (targetFile.exists()) {
                if (targetFile.length() == assetSize) {
                    return targetFile
                }
                targetFile.delete()
            }

            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return targetFile
        } catch (exception: FileNotFoundException) {
            val message = "Failed to copy ExecuTorch model asset: assetPath=$assetPath, error=${exception.message}"
            Log.e(TAG, message, exception)
            File(File(context.filesDir, MODEL_DIR_NAME), fileName).delete()
            throw IllegalStateException(message, exception)
        } catch (exception: IOException) {
            val message = "Failed to copy ExecuTorch model asset: assetPath=$assetPath, error=${exception.message}"
            Log.e(TAG, message, exception)
            File(File(context.filesDir, MODEL_DIR_NAME), fileName).delete()
            throw IllegalStateException(message, exception)
        }
    }

    companion object {
        private const val TAG = "ExecutorchModelAssetCopier"
        const val MODEL_DIR_NAME = "models"
        const val DEFAULT_MODEL_ASSET_PATH = "models/yolo26n.pte"
        const val DEFAULT_MODEL_FILE_NAME = "yolo26n.pte"
    }
}
