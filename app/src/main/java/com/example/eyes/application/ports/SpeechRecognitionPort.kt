package com.example.eyes.application.ports

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.voice.SttResult
import com.example.eyes.domain.voice.SttState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SpeechRecognitionPort {
    val state: StateFlow<SttState>
    val results: SharedFlow<SttResult>

    fun startListening(language: AppLanguage = AppLanguage.VI)
    fun stopListening()
    fun cancel()
}
