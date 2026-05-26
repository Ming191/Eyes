package com.example.eyes.system

import android.content.Context
import android.content.Intent
import android.net.Uri

class EmergencyCallService(
    private val context: Context,
    private val speechOutput: SpeechOutput,
    private val hapticService: HapticService
) {

    fun openDialer(phoneNumber: String) {
        val sanitizedNumber = phoneNumber.filter { it.isDigit() || it == '+' }.ifBlank { "115" }
        speechOutput.speak("Đang mở trình gọi khẩn cấp tới số $sanitizedNumber.")
        hapticService.confirm()

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$sanitizedNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
