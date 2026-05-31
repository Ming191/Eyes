package com.example.eyes.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.ui.camera.CameraMode
import com.example.eyes.ui.theme.EyesTheme

@PreviewTest
@Preview(name = "Home screen", showBackground = true)
@Composable
fun homeScreenDefaultPreview() {
    EyesTheme {
        HomeScreen(
            onOpenOcrQuick = {},
            onOpenOcrAccuracy = {},
            onOpenCameraMode = { _: CameraMode -> },
            onOpenSettings = {},
            viewModel = HomeViewModel(
                context = LocalContext.current,
                tts = FakeSpeechOutput()
            )
        )
    }
}

private class FakeSpeechOutput : SpeechOutput {
    override fun speak(text: String) = Unit
}
