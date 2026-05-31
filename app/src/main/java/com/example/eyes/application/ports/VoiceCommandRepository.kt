package com.example.eyes.application.ports

import com.example.eyes.domain.voice.VoiceCommand
import kotlinx.coroutines.flow.Flow

interface VoiceCommandRepository {
    val lastVoiceCommandFlow: Flow<VoiceCommand?>

    suspend fun setLastVoiceCommand(command: VoiceCommand)
    suspend fun clearLastVoiceCommand()
}
