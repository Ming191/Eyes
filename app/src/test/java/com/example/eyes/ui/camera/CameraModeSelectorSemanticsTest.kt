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
        // GIVEN
        composeTestRule.setContent {
            MaterialTheme {
                CameraModeSelector(
                    activeMode = CameraMode.OBSTACLE,
                    onModeSelected = {}
                )
            }
        }

        // WHEN
        val obstacleNode = composeTestRule.onNodeWithContentDescription(
            "Chuyển sang chế độ phát hiện vật cản"
        )
        val ocrNode = composeTestRule.onNodeWithContentDescription(
            "Chuyển sang chế độ đọc chữ OCR"
        )

        // THEN
        obstacleNode
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Đang chọn"
                )
            )

        ocrNode
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Chưa chọn"
                )
            )
    }

    @Test
    fun modeSelector_clickOcr_invokesSelectionCallback() {
        // GIVEN
        var selectedMode: CameraMode? = null
        composeTestRule.setContent {
            MaterialTheme {
                CameraModeSelector(
                    activeMode = CameraMode.OBSTACLE,
                    onModeSelected = { selectedMode = it }
                )
            }
        }

        // WHEN
        composeTestRule.onNodeWithContentDescription("Chuyển sang chế độ đọc chữ OCR")
            .performClick()

        // THEN
        assertEquals(CameraMode.OCR, selectedMode)
    }
}
