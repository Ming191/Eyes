package com.example.eyes.domain.voice

import kotlinx.coroutines.flow.Flow

interface VoiceCommandRepository {
    val lastVoiceCommandFlow: Flow<VoiceCommand?>

    suspend fun setLastVoiceCommand(command: VoiceCommand)
    suspend fun clearLastVoiceCommand()
}
