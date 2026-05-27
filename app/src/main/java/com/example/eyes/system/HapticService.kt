package com.example.eyes.system

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticService(context: Context) {
    private val appContext = context.applicationContext
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun confirm() {
        vibrate(longArrayOf(0, 45, 35, 45))
    }

    fun loading() {
        vibrate(longArrayOf(0, 20, 30, 20, 30, 20))
    }

    fun error() {
        vibrate(longArrayOf(0, 200, 50, 120, 50, 200))
    }

    private fun vibrate(pattern: LongArray) {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ACCESSIBILITY)
                .build()
            deviceVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), attributes)
        } else {
            deviceVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}