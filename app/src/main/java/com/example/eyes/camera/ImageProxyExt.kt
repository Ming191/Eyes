package com.example.eyes.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

fun ImageProxy.toBitmapWithRotation(): Bitmap {
    val bmp = this.toBitmap()
    val rotation = this.imageInfo.rotationDegrees
    if (rotation == 0) return bmp
    
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotatedBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    
    //Giải phóng bitmap trung gian để tránh tràn bộ nhớ
    if (rotatedBmp != bmp) {
        bmp.recycle()
    }
    return rotatedBmp
}
