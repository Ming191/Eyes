package com.example.eyes.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.image.ImageFormat as DomainImageFormat
import java.io.ByteArrayOutputStream

fun ImageProxy.toBitmapWithRotation(): Bitmap {
    val bitmap = when (format) {
        ImageFormat.JPEG -> toBitmapFromJpeg()
        ImageFormat.YUV_420_888 -> toBitmapFromYuv()
        else -> throw IllegalStateException("Unsupported image format: $format")
    }

    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun Bitmap.toImageFrame(
    format: DomainImageFormat = DomainImageFormat.JPEG,
    quality: Int = 95,
    timestampMillis: Long? = null
): ImageFrame {
    val compressFormat = when (format) {
        DomainImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        else -> Bitmap.CompressFormat.PNG
    }
    val output = ByteArrayOutputStream()
    compress(compressFormat, quality, output)
    return ImageFrame(
        data = output.toByteArray(),
        width = width,
        height = height,
        format = format,
        rotationDegrees = 0,
        timestampMillis = timestampMillis
    )
}

fun ImageFrame.toBitmap(): Bitmap {
    return BitmapFactory.decodeByteArray(data, 0, data.size)
        ?: throw IllegalStateException("Failed to decode ImageFrame")
}

private fun ImageProxy.toBitmapFromJpeg(): Bitmap {
    val plane = planes.firstOrNull()
        ?: throw IllegalStateException("JPEG image has no planes")
    val buffer = plane.buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Failed to decode JPEG image")
}

private fun ImageProxy.toBitmapFromYuv(): Bitmap {
    val nv21 = yuv420888ToNv21()
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val outputStream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 95, outputStream)
    val jpegBytes = outputStream.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        ?: throw IllegalStateException("Failed to decode YUV image")
}

private fun ImageProxy.yuv420888ToNv21(): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

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
        val bytesToRead = minOf(rowStride, buffer.remaining())
        buffer.get(rowData, 0, bytesToRead)
        var inputPos = 0
        for (col in 0 until width) {
            out[outputPos] = rowData[inputPos]
            outputPos += outPixelStride
            inputPos += pixelStride
        }
    }
}
