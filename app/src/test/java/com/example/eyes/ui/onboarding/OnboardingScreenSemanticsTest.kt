package com.example.eyes.ui.onboarding

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.i18n.localizedFor
import com.example.eyes.ui.testing.RobolectricComposeHost
import com.example.eyes.ui.theme.EyesTheme
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OnboardingScreenSemanticsTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val composeHost = RobolectricComposeHost()

    @Before
    fun setUp() {
        composeHost.start()
    }

    @After
    fun tearDown() {
        composeHost.dispose()
    }

    @Test
    fun onboarding_startsWithLanguageStep_andShowsTopBar() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()
            .localizedFor(AppLanguage.VI)

        // WHEN
        composeHost.setContent(composeTestRule) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides context
            ) {
                EyesTheme {
                    OnboardingScreen(
                        selectedLanguage = AppLanguage.VI,
                        onLanguageSelected = {},
                        onFinish = {}
                    )
                }
            }
        }

        // THEN
        composeTestRule.onNodeWithContentDescription("Thanh tiêu đề màn hình giới thiệu")
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Bước 1")[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Chọn ngôn ngữ")[0]
            .assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Tiếng Việt").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("English").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Quay lại").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithContentDescription("Bước một, chọn ngôn ngữ", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun onboarding_englishContext_updatesGuidanceTextImmediately() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()
            .localizedFor(AppLanguage.EN)

        // WHEN
        composeHost.setContent(composeTestRule) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides context
            ) {
                EyesTheme {
                    OnboardingScreen(
                        selectedLanguage = AppLanguage.EN,
                        onLanguageSelected = {},
                        onFinish = {}
                    )
                }
            }
        }

        // THEN
        composeTestRule.onAllNodesWithText("Step 1")[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Choose language")[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("updates interface text", substring = true)[0]
            .assertIsDisplayed()
    }
}
