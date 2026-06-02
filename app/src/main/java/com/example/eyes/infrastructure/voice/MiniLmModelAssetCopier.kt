package com.example.eyes.infrastructure.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

internal class MiniLmModelAssetCopier(
    private val context: Context
) {
    fun copyIfAvailable(targetDir: File): Boolean {
        targetDir.mkdirs()
        return runCatching {
            copyAssetIfMissing(MODEL_ASSET, File(targetDir, MODEL_FILE))
            copyAssetIfMissing(TOKENIZER_ASSET, File(targetDir, TOKENIZER_FILE))
            true
        }.getOrElse { error ->
            if (error !is FileNotFoundException) {
                Log.w(TAG, "Could not copy MiniLM model assets", error)
            }
            false
        }
    }

    private fun copyAssetIfMissing(assetPath: String, targetFile: File) {
        if (targetFile.isFile && targetFile.length() > 0L) return
        context.assets.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private companion object {
        private const val TAG = "MiniLmAssetCopier"
        private const val MODEL_FILE = "model_int8.onnx"
        private const val TOKENIZER_FILE = "tokenizer.json"
        private const val MODEL_ASSET = "models/minilm/$MODEL_FILE"
        private const val TOKENIZER_ASSET = "models/minilm/$TOKENIZER_FILE"
    }
}
