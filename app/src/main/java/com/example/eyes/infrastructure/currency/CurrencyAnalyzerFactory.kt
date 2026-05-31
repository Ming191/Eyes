package com.example.eyes.infrastructure.currency

import android.content.Context
import com.example.eyes.application.ports.CurrencyRecognizerFactory
import com.example.eyes.application.ports.CurrencyRecognizerPort
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.infrastructure.camera.toBitmap

class CurrencyAnalyzerFactory(private val context: Context) : CurrencyRecognizerFactory {
    override fun create(onResult: (label: String, confidence: Float) -> Unit): CurrencyRecognizerPort =
        CurrencyAnalyzerAdapter(CurrencyAnalyzer(context, onResult))
}

private class CurrencyAnalyzerAdapter(private val analyzer: CurrencyAnalyzer) : CurrencyRecognizerPort {
    override fun analyze(imageFrame: ImageFrame) = analyzer.analyze(imageFrame.toBitmap())
    override fun resetBuffer() = analyzer.resetBuffer()
    override fun close() = analyzer.close()
}
