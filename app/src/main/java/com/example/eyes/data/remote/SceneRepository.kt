package com.example.eyes.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.eyes.R
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SceneDescriptionResult {
    data class Success(val text: String) : SceneDescriptionResult
    data class Failure(val userMessage: String) : SceneDescriptionResult
}

class SceneRepository(
    private val localizedTextProvider: LocalizedTextProvider,
    private val sceneDescriptionEngine: SceneDescriptionEngine,
    private val networkChecker: () -> Boolean = { isNetworkAvailable(localizedTextProvider.applicationContext) }
) {

    suspend fun describeScene(bitmap: Bitmap, language: AppLanguage): SceneDescriptionResult = withContext(Dispatchers.IO) {
        val offlineFallback = localizedTextProvider.getString(
            R.string.scene_description_offline_fallback,
            language
        )
        if (!networkChecker()) {
            return@withContext SceneDescriptionResult.Success(offlineFallback)
        }

        try {
            val description = sceneDescriptionEngine.describe(bitmap = bitmap, language = language).trim()
            if (description.isBlank()) {
                return@withContext SceneDescriptionResult.Failure(
                    localizedTextProvider.getString(R.string.scene_description_error_generic, language)
                )
            }
            SceneDescriptionResult.Success(description)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SceneDescriptionEngineException) {
            Log.e(TAG, "Scene description engine failed: ${error.type}", error)
            SceneDescriptionResult.Failure(
                messageForErrorType(type = error.type, language = language)
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Scene description failed", error)
            SceneDescriptionResult.Failure(
                localizedTextProvider.getString(R.string.scene_description_error_generic, language)
            )
        }
    }

    private fun messageForErrorType(type: SceneDescriptionErrorType, language: AppLanguage): String {
        val resId = when (type) {
            SceneDescriptionErrorType.API_KEY_MISSING -> R.string.scene_description_error_api_key_missing
            SceneDescriptionErrorType.UNAUTHORIZED -> R.string.scene_description_error_unauthorized
            SceneDescriptionErrorType.RATE_LIMIT -> R.string.scene_description_error_quota
            SceneDescriptionErrorType.TIMEOUT -> R.string.scene_description_error_timeout
            SceneDescriptionErrorType.EMPTY_RESPONSE,
            SceneDescriptionErrorType.UNKNOWN -> R.string.scene_description_error_generic
        }
        return localizedTextProvider.getString(resId, language)
    }

    private companion object {
        private const val TAG = "SceneRepository"

        private fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
    }
}
