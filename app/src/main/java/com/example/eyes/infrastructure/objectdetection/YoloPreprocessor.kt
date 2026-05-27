package com.example.eyes.infrastructure.objectdetection

import android.graphics.Bitmap

class YoloPreprocessor(
    private val inputSize: Int = YoloExecutorchDetector.DEFAULT_INPUT_SIZE
) {
    fun preprocess(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (resized !== bitmap) resized.recycle()

        val input = FloatArray(3 * inputSize * inputSize)
        val channelSize = inputSize * inputSize
        for (index in pixels.indices) {
            val pixel = pixels[index]
            input[index] = ((pixel shr 16) and 0xFF) / 255f
            input[channelSize + index] = ((pixel shr 8) and 0xFF) / 255f
            input[channelSize * 2 + index] = (pixel and 0xFF) / 255f
        }
        return input
    }
}
