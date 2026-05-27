package com.example.eyes.data.scene

import android.graphics.Bitmap
import com.example.eyes.data.remote.SceneDescriptionResult
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionRepository
import com.example.eyes.domain.i18n.AppLanguage

class SceneRepositorySceneDescriptionRepository(
    private val sceneRepository: SceneRepository
) : SceneDescriptionRepository {
    override suspend fun describeScene(bitmap: Bitmap, language: AppLanguage): SceneDescription {
        return when (val result = sceneRepository.describeScene(bitmap = bitmap, language = language)) {
            is SceneDescriptionResult.Success -> SceneDescription.Success(result.text)
            is SceneDescriptionResult.Failure -> SceneDescription.Failure(result.userMessage)
        }
    }
}
