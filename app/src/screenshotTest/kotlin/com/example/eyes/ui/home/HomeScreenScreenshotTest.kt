package com.example.eyes.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.eyes.application.home.AnnounceHomeGreetingUseCase
import com.example.eyes.application.home.BuildHomeStateUseCase
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.data.i18n.AndroidHomeAnnouncementTextProvider
import com.example.eyes.data.i18n.AndroidHomeTextProvider
import com.example.eyes.infrastructure.i18n.AndroidLocalizedTextProvider
import com.example.eyes.ui.camera.CameraMode
import com.example.eyes.ui.theme.EyesTheme

@PreviewTest
@Preview(name = "Home screen", showBackground = true)
@Composable
fun homeScreenDefaultPreview() {
    val localizedTextProvider = AndroidLocalizedTextProvider(LocalContext.current)
    val homeTextProvider = AndroidHomeTextProvider(localizedTextProvider)
    val homeAnnouncementTextProvider = AndroidHomeAnnouncementTextProvider(localizedTextProvider)

    EyesTheme {
        HomeScreen(
            onOpenOcrQuick = {},
            onOpenOcrAccuracy = {},
            onOpenCameraMode = { _: CameraMode -> },
            onOpenEmergency = {},
            viewModel = HomeViewModel(
                buildHomeState = BuildHomeStateUseCase(homeTextProvider),
                announceHomeGreeting = AnnounceHomeGreetingUseCase(
                    homeAnnouncementTextProvider,
                    FakeSpeechOutput()
                )
            )
        )
    }
}

private class FakeSpeechOutput : SpeechOutput {
    override fun speak(text: String) = Unit
}
