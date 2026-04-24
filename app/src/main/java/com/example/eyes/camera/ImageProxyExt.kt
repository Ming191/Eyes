package com.example.eyes.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toBitmapWithRotation(): Bitmap {
    val bitmap = toBitmapYuv420888()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun ImageProxy.toBitmapYuv420888(): Bitmap {
    val nv21 = yuv420888ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val jpegBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val ySize = width * height
    val uvSize = width * height / 4
    val out = ByteArray(ySize + uvSize * 2)

    imagePlaneToByteArray(yPlane, width, height, out, 0, 1)
    imagePlaneToByteArray(vPlane, width / 2, height / 2, out, ySize, 2)
    imagePlaneToByteArray(uPlane, width / 2, height / 2, out, ySize + 1, 2)

    return out
}

private fun imagePlaneToByteArray(
    plane: ImageProxy.PlaneProxy,
    width: Int,
    height: Int,
    out: ByteArray,
    outOffset: Int,
    outPixelStride: Int
) {
    val buffer = plane.buffer
    buffer.rewind()

    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val rowData = ByteArray(rowStride)
    var outputPos = outOffset

    for (row in 0 until height) {
        val bytesToRead = if (buffer.remaining() >= rowStride) rowStride else buffer.remaining()
        buffer.get(rowData, 0, bytesToRead)
        var inputPos = 0
        for (col in 0 until width) {
            out[outputPos] = rowData[inputPos]
            outputPos += outPixelStride
            inputPos += pixelStride
        }
    }
}
