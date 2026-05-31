package com.example.eyes.application.scene

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.application.ports.SceneDescriptionRepository
import com.example.eyes.domain.i18n.AppLanguage

class DescribeSceneUseCase(
    private val sceneDescriptionRepository: SceneDescriptionRepository
) {
    suspend operator fun invoke(imageFrame: ImageFrame, language: AppLanguage): SceneDescription {
        return sceneDescriptionRepository.describeScene(imageFrame = imageFrame, language = language)
    }
}
