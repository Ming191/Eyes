package com.example.eyes.ui.home

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.system.SpeechOutput
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeScreenSemanticsTest {

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
    fun homeActions_haveAccessibleDescriptions_andClickActions() {
        // GIVEN
        val viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), FakeSpeechOutput())

        // WHEN
        composeTestRule.setContent {
            HomeContent(
                uiState = viewModel.uiState.value,
                onActionSelected = { action ->
                    when (action) {
                        HomeActionType.ReadTextQuick -> Unit
                        HomeActionType.ReadTextAccuracy -> Unit
                        HomeActionType.DescribeScene -> Unit
                        HomeActionType.DetectObjects -> Unit
                        HomeActionType.RecognizeCurrency -> Unit
                        HomeActionType.Voice -> Unit
                        HomeActionType.Settings -> Unit
                    }
                }
            )
        }

        // THEN
        composeTestRule.onNodeWithText("Đọc văn bản nhanh")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "Đọc văn bản nhanh",
            substring = true
        )
            .assertHasClickAction()

        composeTestRule.onNodeWithText("Đọc văn bản chính xác")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "Đọc văn bản chính xác",
            substring = true
        )
            .assertHasClickAction()

        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Ra lệnh bằng giọng nói"))

        composeTestRule.onNodeWithText("Ra lệnh bằng giọng nói")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "Ra lệnh bằng giọng nói",
            substring = true
        )
            .assertHasClickAction()

        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Tinh chỉnh phản hồi"))

        composeTestRule.onNodeWithText("Tinh chỉnh phản hồi")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "Tinh chỉnh phản hồi",
            substring = true
        )
            .assertHasClickAction()
    }

    private class FakeSpeechOutput : SpeechOutput {
        override fun speak(text: String) = Unit
        override fun speak(text: String, priority: SpeechOutput.Priority) = Unit
    }
}
