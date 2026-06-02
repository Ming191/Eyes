package com.example.eyes.data.scene

import com.example.eyes.data.remote.SceneDescriptionEngine
import com.example.eyes.data.remote.SceneRepository
import com.example.eyes.domain.image.ImageFormat
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.scene.SceneDescription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneRepositorySceneDescriptionRepositoryTest {
    @Test
    fun describeSceneDelegatesToSceneRepository() = runTest {
        val frame = ImageFrame(byteArrayOf(1), 1, 1, ImageFormat.JPEG, 0, 1)
        val remote = SceneRepository(
            sceneDescriptionEngine = object : SceneDescriptionEngine {
                override suspend fun describe(imageFrame: ImageFrame, language: AppLanguage): String {
                    assertEquals(frame, imageFrame)
                    assertEquals(AppLanguage.EN, language)
                    return " chair "
                }
            },
            networkChecker = { true }
        )
        val repository = SceneRepositorySceneDescriptionRepository(remote)

        assertEquals(SceneDescription.Success("chair"), repository.describeScene(frame, AppLanguage.EN))
    }
}
