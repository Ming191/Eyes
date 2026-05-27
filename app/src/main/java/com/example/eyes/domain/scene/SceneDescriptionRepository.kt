package com.example.eyes.domain.scene

import android.graphics.Bitmap
import com.example.eyes.i18n.AppLanguage

interface SceneDescriptionRepository {
    suspend fun describeScene(bitmap: Bitmap, language: AppLanguage): SceneDescription
}
