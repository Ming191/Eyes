package com.example.eyes.application.navigation

import com.example.eyes.data.DataStoreManager
import com.example.eyes.ocr.OcrMode

class SetCameraOcrModeUseCase(
    private val dataStoreManager: DataStoreManager
) {
    suspend operator fun invoke(mode: OcrMode) {
        dataStoreManager.setOcrMode(mode)
    }
}
