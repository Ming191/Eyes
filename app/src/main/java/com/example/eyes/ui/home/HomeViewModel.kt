package com.example.eyes.ui.home

import androidx.lifecycle.ViewModel
import com.example.eyes.system.SpeechOutput

class HomeViewModel(
    private val tts: SpeechOutput
) : ViewModel() {
    fun greet() {
        tts.speak("Chào mừng. Nhấn Xem, Đọc hoặc Đi để bắt đầu.")
    }
}
