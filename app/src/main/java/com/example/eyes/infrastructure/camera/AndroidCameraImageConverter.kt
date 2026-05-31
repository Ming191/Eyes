package com.example.eyes.infrastructure.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.eyes.domain.image.ImageFrame

class AndroidCameraImageConverter : CameraImageConverter {
    override fun toBitmapWithRotation(imageProxy: ImageProxy): Bitmap = imageProxy.toBitmapWithRotation()

    override fun toImageFrame(bitmap: Bitmap): ImageFrame = bitmap.toImageFrame()
}
