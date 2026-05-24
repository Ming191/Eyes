package com.example.eyes.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.eyes.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

class SceneRepository(
    private val context: Context,
    private val api: SceneApi
) {

    /**
     * Provides a scene description by uploading the given bitmap to the remote API and
     * falling back to a generic retry prompt when no network is available or the API returns no meaningful text.
     *
     * @param bitmap The scene image to upload.
     * @return The API-provided description trimmed of surrounding whitespace if non-blank, otherwise a fallback retry prompt.
     */
    suspend fun describeScene(bitmap: Bitmap): String = withContext(Dispatchers.IO) {

        val fallback = context.getString(R.string.scene_offline_fallback)

        if (!isNetworkAvailable()) {
            return@withContext fallback
        }

        runCatching {
            val body = bitmap
                .resizeForUpload(maxSize = 512)
                .toJpegByteArray()
                .toRequestBody(JPEG_MEDIA_TYPE)

            val part = MultipartBody.Part.createFormData(
                name = "file",
                filename = "scene.jpg",
                body = body
            )

            val response = api.describe(part)

            response.text
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallback

        }.getOrElse { e ->
            Log.e("SceneDescriber", "Describe failed", e)
            fallback
        }
    }

    /**
     * Checks whether the device currently has an active network that provides internet capability.
     *
     * @return `true` if an active network with `NET_CAPABILITY_INTERNET` is available, `false` otherwise.
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Scale the bitmap so its largest edge does not exceed the specified maximum while preserving aspect ratio.
     *
     * @param maxSize Maximum length in pixels for the bitmap's longest side.
     * @return A bitmap whose largest edge is less than or equal to `maxSize`; returns the original bitmap if no scaling was needed.
     */
    private fun Bitmap.resizeForUpload(maxSize: Int): Bitmap {
        val largestEdge = maxOf(width, height)
        if (largestEdge <= maxSize) return this

        val scale = maxSize.toFloat() / largestEdge
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return this.scale(targetWidth, targetHeight)
    }

    /**
     * Encodes the bitmap as JPEG and returns the encoded bytes.
     *
     * @param quality JPEG compression quality from 0 (lowest) to 100 (highest); defaults to 85.
     * @return A byte array containing the JPEG-encoded image data.
     */
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
