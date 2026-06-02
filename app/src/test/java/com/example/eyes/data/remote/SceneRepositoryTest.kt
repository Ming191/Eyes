package com.example.eyes.data.remote

import com.example.eyes.domain.image.ImageFormat
import com.example.eyes.domain.image.ImageFrame
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.scene.SceneDescription
import com.example.eyes.domain.scene.SceneDescriptionError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SceneRepositoryTest {

    @Test
    fun describeScene_returnsOfflineWithoutCallingEngine_whenNetworkUnavailable() = runBlocking {
        var calls = 0
        val repository = SceneRepository(
            sceneDescriptionEngine = FakeSceneDescriptionEngine { _, _ ->
                calls++
                "unused"
            },
            networkChecker = { false }
        )

        val result = repository.describeScene(testFrame(), AppLanguage.EN)

        assertEquals(SceneDescription.Failure(SceneDescriptionError.OFFLINE), result)
        assertEquals(0, calls)
    }

    @Test
    fun describeScene_trimsNonBlankEngineResponse() = runBlocking {
        val repository = SceneRepository(
            sceneDescriptionEngine = FakeSceneDescriptionEngine { imageFrame, language ->
                assertEquals(testFrame(), imageFrame)
                assertEquals(AppLanguage.VI, language)
                "  Có một cái ghế.  "
            },
            networkChecker = { true }
        )

        val result = repository.describeScene(testFrame(), AppLanguage.VI)

        assertEquals(SceneDescription.Success("Có một cái ghế."), result)
    }

    @Test
    fun describeScene_mapsBlankAndEngineErrors() = runBlocking {
        val blankRepository = SceneRepository(FakeSceneDescriptionEngine { _, _ -> "   " }) { true }
        val unauthorizedRepository = SceneRepository(
            FakeSceneDescriptionEngine { _, _ ->
                throw SceneDescriptionEngineException(SceneDescriptionErrorType.UNAUTHORIZED, "bad key")
            }
        ) { true }
        val unknownRepository = SceneRepository(FakeSceneDescriptionEngine { _, _ -> error("boom") }) { true }

        assertEquals(
            SceneDescription.Failure(SceneDescriptionError.EMPTY_RESPONSE),
            blankRepository.describeScene(testFrame(), AppLanguage.EN)
        )
        assertEquals(
            SceneDescription.Failure(SceneDescriptionError.UNAUTHORIZED),
            unauthorizedRepository.describeScene(testFrame(), AppLanguage.EN)
        )
        assertEquals(
            SceneDescription.Failure(SceneDescriptionError.UNKNOWN),
            unknownRepository.describeScene(testFrame(), AppLanguage.EN)
        )
    }

    @Test(expected = CancellationException::class)
    fun describeScene_rethrowsCancellation() = runBlocking {
        val repository = SceneRepository(
            FakeSceneDescriptionEngine { _, _ -> throw CancellationException("cancelled") }
        ) { true }

        repository.describeScene(testFrame(), AppLanguage.EN)
        Unit
    }

    private class FakeSceneDescriptionEngine(
        private val response: suspend (ImageFrame, AppLanguage) -> String
    ) : SceneDescriptionEngine {
        override suspend fun describe(imageFrame: ImageFrame, language: AppLanguage): String =
            response(imageFrame, language)
    }

    private fun testFrame(): ImageFrame = ImageFrame(
        data = byteArrayOf(1, 2, 3),
        width = 1,
        height = 1,
        format = ImageFormat.JPEG,
        rotationDegrees = 90,
        timestampMillis = 123L
    )
}
