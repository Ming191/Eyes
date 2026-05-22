package com.example.eyes.ui.camera

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraModeSelectorSemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        runCatching { stopKoin() }
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun modeSelector_hasAccessibleModeActions_andStateDescriptions() {
        composeTestRule.setContent {
            MaterialTheme {
                CameraModeSelector(
                    activeMode = CameraMode.OBSTACLE,
                    onModeSelected = {}
                )
            }
        }

        val obstacleNode = composeTestRule.onNodeWithContentDescription(
            "Switch to obstacle detection mode"
        )
        val ocrNode = composeTestRule.onNodeWithContentDescription(
            "Switch to OCR text reading mode"
        )

        obstacleNode
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Selected"
                )
            )

        ocrNode
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Not selected"
                )
            )
    }

    @Test
    fun modeSelector_clickOcr_invokesSelectionCallback() {
        var selectedMode: CameraMode? = null
        composeTestRule.setContent {
            MaterialTheme {
                CameraModeSelector(
                    activeMode = CameraMode.OBSTACLE,
                    onModeSelected = { selectedMode = it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Switch to OCR text reading mode")
            .performClick()

        assertEquals(CameraMode.OCR, selectedMode)
    }
}
