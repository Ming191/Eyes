package com.example.eyes.infrastructure.objectdetection

import android.graphics.Bitmap
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class YoloExecutorchDetectorSeamTest {
    @Test
    fun inspectOutputShapeMapsFakeModelOutput() = runTest {
        val detector = YoloExecutorchDetector(fakeLoader(listOf(YoloExecutorchTensor(floatArrayOf(1f), longArrayOf(1, 2, 3), "FLOAT32", 6))))

        val info = detector.inspectOutputShape().single()

        assertEquals(0, info.index)
        assertEquals(listOf(1L, 2L, 3L), info.shape)
        assertEquals("FLOAT32", info.dtype)
        assertEquals(6, info.elementCount)
    }

    @Test
    fun detectReturnsEmptyWhenModelReturnsNoOutputs() = runTest {
        val detector = YoloExecutorchDetector(fakeLoader(emptyList()))
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

        assertTrue(detector.detect(bitmap).isEmpty())
    }

    @Test
    fun detectReturnsEmptyWhenModelThrows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val copier = ExecutorchModelAssetCopier(context) { ByteArrayInputStream(byteArrayOf(1)) }
        val loader = YoloExecutorchModelLoader(copier) { error("boom") }
        val detector = YoloExecutorchDetector(loader)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

        assertTrue(detector.detect(bitmap).isEmpty())
    }

    @Test
    fun modelLoaderReturnsCachedModelWithoutNativeLoad() {
        val loader = fakeLoader(emptyList())

        assertTrue(loader.load() === loader.load())
    }

    private fun fakeLoader(outputs: List<YoloExecutorchTensor>): YoloExecutorchModelLoader {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val copier = ExecutorchModelAssetCopier(context) { ByteArrayInputStream(byteArrayOf(1)) }
        return YoloExecutorchModelLoader(copier) { error("native loader unused") }.also {
            val field = YoloExecutorchModelLoader::class.java.getDeclaredField("module")
            field.isAccessible = true
            field.set(it, object : YoloExecutorchModel {
                override fun forward(input: FloatArray, shape: LongArray): List<YoloExecutorchTensor> = outputs
            })
        }
    }
}
