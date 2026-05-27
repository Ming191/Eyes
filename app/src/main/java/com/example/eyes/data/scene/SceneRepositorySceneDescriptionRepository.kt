package com.example.eyes.data.scene

import android.graphics.Bitmap
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionRepository

class SceneRepositorySceneDescriptionRepository(
    private val sceneRepository: SceneRepository
) : SceneDescriptionRepository {
    override suspend fun describeScene(bitmap: Bitmap, language: AppLanguage): SceneDescription {
        return sceneRepository.describeScene(bitmap = bitmap, language = language)
    }
}
