package com.example.eyes.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.eyes.ai.Detection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class SceneRepository(
    private val context: Context,
    private val api: SceneApi
) {

    suspend fun describeScene(
        bitmap: Bitmap,
        detections: List<Detection>
    ): String {
        val fallback = buildOfflineDescription(detections)
        if (!isNetworkAvailable()) return fallback

        return runCatching {
            val body = bitmap
                .resizeForUpload(maxSize = 512)
                .toJpegByteArray()
                .toRequestBody(JPEG_MEDIA_TYPE)

            val part = MultipartBody.Part.createFormData(
                name = "file",
                filename = "scene.jpg",
                body = body
            )

            val result = api.describe(part).text
            result?.trim().takeUnless { it.isNullOrBlank() } ?: fallback
        }.getOrDefault(fallback)
    }

    fun buildOfflineDescription(detections: List<Detection>): String {
        if (detections.isEmpty()) {
            return "Tôi chưa phát hiện vật cản rõ ràng. Bạn hãy giữ điện thoại ổn định và thử lại."
        }

        val parts = detections
            .sortedByDescending { it.confidence }
            .take(3)
            .map { detection -> "${detection.labelVi} phía ${detection.zone.labelVi}" }

        return "Phía trước có ${parts.joinToString(", ")}."
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun Bitmap.resizeForUpload(maxSize: Int): Bitmap {
        val largestEdge = maxOf(width, height)
        if (largestEdge <= maxSize) return this

        val scale = maxSize.toFloat() / largestEdge
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.toJpegByteArray(quality: Int = 85): ByteArray {
        return ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
    }

    private companion object {
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
