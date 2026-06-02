package com.example.eyes.application.voice

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.voice.VoiceIntent

interface SemanticVoiceCommandMatcher {
    fun match(text: String, language: AppLanguage): VoiceIntent?
}

class NoOpSemanticVoiceCommandMatcher : SemanticVoiceCommandMatcher {
    override fun match(text: String, language: AppLanguage): VoiceIntent? = null
}
