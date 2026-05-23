package com.example.eyes.ui.home

import androidx.test.core.app.ApplicationProvider
import com.example.eyes.system.SpeechOutput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @Before
    fun setUp() {
        runCatching { stopKoin() }
    }

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun onScreenShown_callsSpeakOnce_withExpectedVietnameseMessage() {
        val fakeSpeechOutput = FakeSpeechOutput()
        val viewModel = HomeViewModel(fakeSpeechOutput, ApplicationProvider.getApplicationContext())

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(1, fakeSpeechOutput.spokenTexts.size)
        assertEquals(
            "Welcome. Choose Look, Read, Money, Go, or Settings to start.",
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
