package com.example.eyes.ui.home

import com.example.eyes.system.SpeechOutput
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun onScreenShown_callsSpeakOnce_withExpectedVietnameseMessage() {
        val fakeSpeechOutput = FakeSpeechOutput()
        val viewModel = HomeViewModel(fakeSpeechOutput)

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(1, fakeSpeechOutput.spokenTexts.size)
        assertEquals(
            "Chào mừng. Chọn Xem, Đọc, Đi hoặc Cài đặt để bắt đầu.",
            fakeSpeechOutput.spokenTexts.single()
        )
    }

    private class FakeSpeechOutput : SpeechOutput {
        val spokenTexts = mutableListOf<String>()
        override fun speak(text: String) {
            spokenTexts.add(text)
        }
        override fun speak(text: String, priority: SpeechOutput.Priority) {
            spokenTexts.add(text)
        }
    }
}
