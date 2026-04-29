package com.example.eyes.ui.home

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
    fun xemXungQuanh_hasAccessibleDescription_andClickAction() {
        // GIVEN
        val viewModel = HomeViewModel(FakeSpeechOutput())

        // WHEN
        composeTestRule.setContent {
            HomeScreen(
                onOpenCamera = {},
                onOpenOcr = {},
                onOpenMap = {},
                onOpenSettings = {},
                viewModel = viewModel
            )
        }

        // THEN
        composeTestRule.onNodeWithText("Xem xung quanh")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "Mở camera để nhận biết vật cản",
            substring = true
        )
            .assertHasClickAction()
    }

    private class FakeSpeechOutput : SpeechOutput {
        override fun speak(text: String) = Unit
    }
}
