package com.example.eyes.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SceneRepository(
    private val sceneDescriptionEngine: SceneDescriptionEngine,
    private val networkChecker: () -> Boolean
) {

    suspend fun describeScene(bitmap: Bitmap, language: AppLanguage): SceneDescription = withContext(Dispatchers.IO) {
        if (!networkChecker()) {
            return@withContext SceneDescription.Failure(SceneDescriptionError.OFFLINE)
        }

        try {
            val description = sceneDescriptionEngine.describe(bitmap = bitmap, language = language).trim()
            if (description.isBlank()) {
                return@withContext SceneDescription.Failure(SceneDescriptionError.EMPTY_RESPONSE)
            }
            SceneDescription.Success(description)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SceneDescriptionEngineException) {
            Log.e(TAG, "Scene description engine failed: ${error.type}", error)
            SceneDescription.Failure(error.type.toSceneDescriptionError())
        } catch (error: Throwable) {
            Log.e(TAG, "Scene description failed", error)
            SceneDescription.Failure(SceneDescriptionError.UNKNOWN)
        }
    }

    private fun SceneDescriptionErrorType.toSceneDescriptionError(): SceneDescriptionError = when (this) {
        SceneDescriptionErrorType.API_KEY_MISSING -> SceneDescriptionError.API_KEY_MISSING
        SceneDescriptionErrorType.UNAUTHORIZED -> SceneDescriptionError.UNAUTHORIZED
        SceneDescriptionErrorType.RATE_LIMIT -> SceneDescriptionError.RATE_LIMIT
        SceneDescriptionErrorType.TIMEOUT -> SceneDescriptionError.TIMEOUT
        SceneDescriptionErrorType.EMPTY_RESPONSE -> SceneDescriptionError.EMPTY_RESPONSE
        SceneDescriptionErrorType.UNKNOWN -> SceneDescriptionError.UNKNOWN
    }

    private companion object {
        private const val TAG = "SceneRepository"
    }
}
