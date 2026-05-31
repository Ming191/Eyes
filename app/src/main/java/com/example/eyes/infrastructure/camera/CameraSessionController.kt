package com.example.eyes.infrastructure.camera

import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

interface CameraSessionController {
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: ((ImageProxy) -> Unit)? = null
    )

    fun takePictureAfterCenterFocus(
        onCaptured: (ImageProxy) -> Unit,
        onError: (ImageCaptureException) -> Unit = {}
    )
}
