package com.example.eyes.system

/**
 * Abstraction for text-to-speech output. Allows ViewModels and other modules
 * to request speech without coupling to the concrete TtsService implementation.
 */
interface SpeechOutput : com.example.eyes.domain.speech.SpeechOutput
