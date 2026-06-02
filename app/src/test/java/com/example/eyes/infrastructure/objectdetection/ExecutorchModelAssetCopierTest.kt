package com.example.eyes.infrastructure.objectdetection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExecutorchModelAssetCopierTest {
    @Test
    fun copyModelToFilesDirCopiesAssetAndReusesMatchingFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, ExecutorchModelAssetCopier.MODEL_DIR_NAME).deleteRecursively()
        var opens = 0
        val copier = ExecutorchModelAssetCopier(context) {
            opens++
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }

        val first = copier.copyModelToFilesDir(assetPath = "asset.pte", fileName = "model.pte")
        val second = copier.copyModelToFilesDir(assetPath = "asset.pte", fileName = "model.pte")

        assertEquals(first.absolutePath, second.absolutePath)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertEquals(3, opens)
    }

    @Test
    fun copyModelToFilesDirReplacesWrongSizedFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, ExecutorchModelAssetCopier.MODEL_DIR_NAME)
        dir.mkdirs()
        File(dir, "model.pte").writeBytes(byteArrayOf(9))
        val copier = ExecutorchModelAssetCopier(context) {
            ByteArrayInputStream(byteArrayOf(4, 5))
        }

        val file = copier.copyModelToFilesDir(assetPath = "asset.pte", fileName = "model.pte")

        assertArrayEquals(byteArrayOf(4, 5), file.readBytes())
    }

    @Test
    fun copyModelToFilesDirDeletesPartialFileWhenAssetMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val copier = ExecutorchModelAssetCopier(context) { throw java.io.FileNotFoundException("missing") }

        val error = runCatching { copier.copyModelToFilesDir(assetPath = "missing", fileName = "missing.pte") }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }
}
