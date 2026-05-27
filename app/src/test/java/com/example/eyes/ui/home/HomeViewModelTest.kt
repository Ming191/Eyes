package com.example.eyes.ui.home

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import com.example.eyes.application.home.AnnounceHomeGreetingUseCase
import com.example.eyes.application.home.BuildHomeStateUseCase
import com.example.eyes.data.i18n.AndroidHomeTextProvider
import com.example.eyes.i18n.AndroidLocalizedTextProvider
import com.example.eyes.system.SpeechOutput
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @Test
    fun onScreenShown_callsSpeakOnce_withExpectedVietnameseMessage() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val localizedTextProvider = AndroidLocalizedTextProvider(application)
        val homeTextProvider = AndroidHomeTextProvider(localizedTextProvider)
        val fakeSpeechOutput = FakeSpeechOutput()
        val viewModel = HomeViewModel(
            buildHomeState = BuildHomeStateUseCase(homeTextProvider),
            announceHomeGreeting = AnnounceHomeGreetingUseCase(localizedTextProvider, fakeSpeechOutput)
        )

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(1, fakeSpeechOutput.spokenTexts.size)
        assertEquals(
            "Chào mừng. Chọn Đọc, Giọng nói hoặc Cài đặt để bắt đầu.",
            fakeSpeechOutput.spokenTexts.single()
        )
    }

    private class FakeSpeechOutput : SpeechOutput {
        val spokenTexts = mutableListOf<String>()
        override fun speak(text: String) {
            spokenTexts.add(text)
        }
    }
}
