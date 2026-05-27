package com.example.eyes.application.ports

import com.example.eyes.domain.image.ImageFrame

interface CurrencyRecognizerPort : AutoCloseable {
    fun analyze(imageFrame: ImageFrame)
    fun resetBuffer()
    override fun close()

    companion object {
        const val EMPTY_LABEL = ""
    }
}
