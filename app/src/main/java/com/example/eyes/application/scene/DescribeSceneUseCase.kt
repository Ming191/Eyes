package com.example.eyes.application.scene

import android.graphics.Bitmap
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionRepository
import com.example.eyes.domain.i18n.AppLanguage

class DescribeSceneUseCase(
    private val sceneDescriptionRepository: SceneDescriptionRepository
) {
    suspend operator fun invoke(bitmap: Bitmap, language: AppLanguage): SceneDescription {
        return sceneDescriptionRepository.describeScene(bitmap = bitmap, language = language)
    }
}
