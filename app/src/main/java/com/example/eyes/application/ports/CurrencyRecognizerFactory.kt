package com.example.eyes.application.ports

fun interface CurrencyRecognizerFactory {
    fun create(onResult: (label: String, confidence: Float) -> Unit): CurrencyRecognizerPort
}
