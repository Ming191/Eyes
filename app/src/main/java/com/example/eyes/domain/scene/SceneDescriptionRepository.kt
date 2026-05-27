package com.example.eyes.domain.scene

import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.i18n.AppLanguage

interface SceneDescriptionRepository {
    suspend fun describeScene(imageFrame: ImageFrame, language: AppLanguage): SceneDescription
}
