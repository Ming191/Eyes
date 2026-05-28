package com.example.eyes.data.scene

import android.graphics.Bitmap
import com.example.eyes.infrastructure.camera.toBitmap
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionRepository

class SceneRepositorySceneDescriptionRepository(
    private val sceneRepository: SceneRepository
) : SceneDescriptionRepository {
    override suspend fun describeScene(imageFrame: ImageFrame, language: AppLanguage): SceneDescription {
        val bitmap: Bitmap = imageFrame.toBitmap()
        return sceneRepository.describeScene(bitmap = bitmap, language = language)
    }
}
