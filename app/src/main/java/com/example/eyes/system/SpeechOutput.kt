package com.example.eyes.system

interface SpeechOutput {
    fun speak(text: String)

    fun setSpeechRate(rate: Float) = Unit
}
