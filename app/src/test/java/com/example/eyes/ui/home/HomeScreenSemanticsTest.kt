package com.example.eyes.ui.home

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.example.eyes.R
import com.example.eyes.application.home.AnnounceHomeGreetingUseCase
import com.example.eyes.application.home.BuildHomeStateUseCase
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.data.i18n.AndroidHomeAnnouncementTextProvider
import com.example.eyes.data.i18n.AndroidHomeTextProvider
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.i18n.AndroidLocalizedTextProvider
import com.example.eyes.ui.testing.RobolectricComposeHost
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
    val composeTestRule = createEmptyComposeRule()

    private val composeHost = RobolectricComposeHost()

    @Before
    fun setUp() {
        runCatching { stopKoin() }
        composeHost.start()
    }

    @After
    fun tearDown() {
        composeHost.dispose()
        runCatching { stopKoin() }
    }

    @Test
    fun homeActions_haveAccessibleDescriptions_andClickActions() {
        // GIVEN
        val application = ApplicationProvider.getApplicationContext<Application>()
        val localizedTextProvider = AndroidLocalizedTextProvider(application)
        val homeAnnouncementTextProvider = AndroidHomeAnnouncementTextProvider(localizedTextProvider)
        val homeTextProvider = AndroidHomeTextProvider(localizedTextProvider)
        val readQuickDescription = localizedTextProvider.getString(
            R.string.home_action_read_quick_accessibility,
            AppLanguage.VI
        )
        val readAccuracyDescription = localizedTextProvider.getString(
            R.string.home_action_read_accuracy_accessibility,
            AppLanguage.VI
        )
        val emergencyDescription = localizedTextProvider.getString(
            R.string.home_action_emergency_accessibility,
            AppLanguage.VI
        )
        val viewModel = HomeViewModel(
            buildHomeState = BuildHomeStateUseCase(homeTextProvider),
            announceHomeGreeting = AnnounceHomeGreetingUseCase(homeAnnouncementTextProvider, FakeSpeechOutput())
        )

        // WHEN
        composeHost.setContent(composeTestRule) {
            HomeContent(
                uiState = viewModel.uiState.value,
                onActionSelected = { action ->
                    when (action) {
                        HomeActionType.ReadTextQuick -> Unit
                        HomeActionType.ReadTextAccuracy -> Unit
                        HomeActionType.DescribeScene -> Unit
                        HomeActionType.DetectObjects -> Unit
                        HomeActionType.RecognizeCurrency -> Unit
                        HomeActionType.EmergencyCall -> Unit
                    }
                },
                onEmergencyNumberSelected = {}
            )
        }

        // THEN
        composeTestRule.onNodeWithContentDescription(
            readQuickDescription
        )
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription(
            readAccuracyDescription
        )
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription(
            emergencyDescription
        )
            .assertHasClickAction()

    }

    private class FakeSpeechOutput : SpeechOutput {
        override fun speak(text: String) = Unit
    }
}
