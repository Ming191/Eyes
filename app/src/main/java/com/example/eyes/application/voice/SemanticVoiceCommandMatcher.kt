package com.example.eyes.application.voice

import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.voice.VoiceCommand

interface SemanticVoiceCommandMatcher {
    fun match(text: String, language: AppLanguage): VoiceCommand?
}

class NoOpSemanticVoiceCommandMatcher : SemanticVoiceCommandMatcher {
    override fun match(text: String, language: AppLanguage): VoiceCommand? = null
}
