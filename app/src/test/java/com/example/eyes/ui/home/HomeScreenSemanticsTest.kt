package com.example.eyes.ui.home

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.application.home.AnnounceHomeGreetingUseCase
import com.example.eyes.application.home.BuildHomeStateUseCase
import com.example.eyes.data.i18n.AndroidHomeAnnouncementTextProvider
import com.example.eyes.data.i18n.AndroidHomeTextProvider
import com.example.eyes.i18n.AndroidLocalizedTextProvider
import com.example.eyes.infrastructure.system.SpeechOutput
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
        val application = ApplicationProvider.getApplicationContext<Application>()
        val localizedTextProvider = AndroidLocalizedTextProvider(application)
        val homeAnnouncementTextProvider = AndroidHomeAnnouncementTextProvider(localizedTextProvider)
        val homeTextProvider = AndroidHomeTextProvider(localizedTextProvider)
        val viewModel = HomeViewModel(
            buildHomeState = BuildHomeStateUseCase(homeTextProvider),
            announceHomeGreeting = AnnounceHomeGreetingUseCase(homeAnnouncementTextProvider, FakeSpeechOutput())
        )

        // WHEN
        composeTestRule.setContent {
            HomeContent(
                uiState = viewModel.uiState.value,
                onActionSelected = { action ->
                    when (action) {
                        HomeActionType.ReadTextQuick -> Unit
                        HomeActionType.DescribeScene -> Unit
                        HomeActionType.DetectObjects -> Unit
                        HomeActionType.RecognizeCurrency -> Unit
                        HomeActionType.EmergencyCall -> Unit
                        HomeActionType.Voice -> Unit
                    }
                },
                onEmergencyNumberSelected = {}
            )
        }

        // THEN
        composeTestRule.onNodeWithContentDescription(
            "Đọc văn bản nhanh",
            substring = true
        )
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription(
            "Gọi khẩn cấp",
            substring = true
        )
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription(
            "Ra lệnh bằng giọng nói",
            substring = true
        )
            .assertHasClickAction()
    }

    private class FakeSpeechOutput : SpeechOutput {
        override fun speak(text: String) = Unit
    }
}
