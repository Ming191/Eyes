package com.example.eyes.ui.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.eyes.domain.image.ImageFrame

interface CameraImageConverter {
    fun toBitmapWithRotation(imageProxy: ImageProxy): Bitmap
    fun toImageFrame(bitmap: Bitmap): ImageFrame
}
