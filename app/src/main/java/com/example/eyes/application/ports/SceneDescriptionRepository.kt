package com.example.eyes.application.ports

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.scene.SceneDescription

interface SceneDescriptionRepository {
    suspend fun describeScene(imageFrame: ImageFrame, language: AppLanguage): SceneDescription
}
