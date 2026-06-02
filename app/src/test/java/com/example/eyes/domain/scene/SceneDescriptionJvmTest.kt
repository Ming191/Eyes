package com.example.eyes.domain.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDescriptionJvmTest {

    @Test
    fun success_storesTextAndImplementsSceneDescription() {
        // GIVEN
        val text = "person near doorway"

        // WHEN
        val description: Any = SceneDescription.Success(text)

        // THEN
        assertEquals(text, (description as SceneDescription.Success).text)
        assertTrue(SceneDescription::class.java.isAssignableFrom(description.javaClass))
    }

    @Test
    fun failure_storesErrorAndImplementsSceneDescription() {
        // GIVEN
        val error = SceneDescriptionError.TIMEOUT

        // WHEN
        val description: Any = SceneDescription.Failure(error)

        // THEN
        assertEquals(error, (description as SceneDescription.Failure).error)
        assertTrue(SceneDescription::class.java.isAssignableFrom(description.javaClass))
    }

    @Test
    fun sceneDescriptionError_valuesRemainInExpectedOrder() {
        // WHEN
        val values = SceneDescriptionError.values().toList()

        // THEN
        assertEquals(
            listOf(
                SceneDescriptionError.OFFLINE,
                SceneDescriptionError.API_KEY_MISSING,
                SceneDescriptionError.UNAUTHORIZED,
                SceneDescriptionError.RATE_LIMIT,
                SceneDescriptionError.TIMEOUT,
                SceneDescriptionError.EMPTY_RESPONSE,
                SceneDescriptionError.UNKNOWN,
            ),
            values,
        )
    }
}
