package com.example.eyes.data.voice

import com.example.eyes.data.DataStoreManager
import com.example.eyes.domain.voice.VoiceCommand
import com.example.eyes.application.ports.VoiceCommandRepository

class DataStoreVoiceCommandRepository(
    private val dataStoreManager: DataStoreManager
) : VoiceCommandRepository {
    override val lastVoiceCommandFlow = dataStoreManager.lastVoiceCommandFlow

    override suspend fun setLastVoiceCommand(command: VoiceCommand) {
        dataStoreManager.setLastVoiceCommand(command)
    }

    override suspend fun clearLastVoiceCommand() {
        dataStoreManager.clearLastVoiceCommand()
    }
}
