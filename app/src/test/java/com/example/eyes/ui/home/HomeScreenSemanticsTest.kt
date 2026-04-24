package com.example.eyes.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.example.eyes.system.SpeechOutput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenSemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun xemXungQuanh_hasAccessibleDescription_andClickAction() {
        // GIVEN
        val viewModel = HomeViewModel(FakeSpeechOutput())

        // WHEN
        composeTestRule.setContent {
            HomeScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        // THEN
        composeTestRule.onNodeWithText("Xem xung quanh")
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "mở camera nhận dạng vật cản",
            substring = true
        )
            .assertHasClickAction()
    }

    private class FakeSpeechOutput : SpeechOutput {
        override fun speak(text: String) = Unit
    }
}
