package com.example.eyes.application.currency

import com.example.eyes.application.ports.CurrencyRecognizerFactory
import com.example.eyes.application.ports.CurrencyRecognizerPort
import com.example.eyes.domain.image.ImageFrame

class RecognizeCurrencyUseCase(
    private val currencyRecognizerFactory: CurrencyRecognizerFactory
) : AutoCloseable {
    private var recognizer: CurrencyRecognizerPort? = null
    private var currentOnResult: ((label: String, confidence: Float) -> Unit)? = null

    fun analyze(
        imageFrame: ImageFrame,
        onResult: (label: String, confidence: Float) -> Unit
    ) {
        getRecognizer(onResult).analyze(imageFrame)
    }

    fun prepare(onResult: (label: String, confidence: Float) -> Unit) {
        getRecognizer(onResult)
    }

    fun resetBuffer() {
        recognizer?.resetBuffer()
    }

    private fun getRecognizer(onResult: (label: String, confidence: Float) -> Unit): CurrencyRecognizerPort {
        val existing = recognizer
        if (existing != null && currentOnResult === onResult) return existing

        existing?.close()
        return currencyRecognizerFactory.create(onResult).also { created ->
            recognizer = created
            currentOnResult = onResult
        }
    }

    override fun close() {
        recognizer?.close()
        recognizer = null
        currentOnResult = null
    }
}
