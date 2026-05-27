package com.example.eyes.infrastructure.currency

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.eyes.application.ports.CurrencyRecognizerFactory
import com.example.eyes.application.ports.CurrencyRecognizerPort

class CurrencyAnalyzerFactory(private val context: Context) : CurrencyRecognizerFactory {
    override fun create(onResult: (label: String, confidence: Float) -> Unit): CurrencyRecognizerPort =
        CurrencyAnalyzerAdapter(CurrencyAnalyzer(context, onResult))
}

private class CurrencyAnalyzerAdapter(private val analyzer: CurrencyAnalyzer) : CurrencyRecognizerPort {
    override fun analyze(imageProxy: ImageProxy) = analyzer.analyze(imageProxy)
    override fun analyze(bitmap: Bitmap) = analyzer.analyze(bitmap)
    override fun resetBuffer() = analyzer.resetBuffer()
    override fun close() = analyzer.close()
}
