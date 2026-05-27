package com.example.eyes.application.ports

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

interface CurrencyRecognizerPort : AutoCloseable {
    fun analyze(imageProxy: ImageProxy)
    fun analyze(bitmap: Bitmap)
    fun resetBuffer()
    override fun close()

    companion object {
        const val EMPTY_LABEL = ""
    }
}
