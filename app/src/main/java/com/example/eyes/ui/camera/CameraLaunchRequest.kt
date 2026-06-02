package com.example.eyes.ui.camera

import androidx.compose.runtime.Immutable
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.domain.voice.VoiceCameraTarget

@Immutable
data class CameraLaunchRequest(
    val id: Long,
    val target: VoiceCameraTarget,
    val ocrMode: OcrMode? = null,
    val autoCapture: Boolean = false
)
