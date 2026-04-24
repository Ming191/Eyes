package com.example.eyes.ui.camera

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.example.eyes.camera.toBitmapWithRotation

class CameraViewModel : ViewModel() {

    fun processFrame(imageProxy: ImageProxy) {
        runCatching {
            imageProxy.toBitmapWithRotation()
        }.onFailure {
            // no-op for this stage
        }
        imageProxy.close()
    }
}
