package com.example.eyes

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.eyes.service.ObstacleDetectionService
import com.example.eyes.ui.navigation.AppNavGraph
import com.example.eyes.ui.theme.EyesTheme

class MainActivity : ComponentActivity() {
    private var lastVolumeDownTapAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EyesTheme {
                AppNavGraph()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastVolumeDownTapAt <= DOUBLE_TAP_WINDOW_MS) {
                lastVolumeDownTapAt = 0L
                ObstacleDetectionService.toggle(this)
                return true
            }
            lastVolumeDownTapAt = now
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        private const val DOUBLE_TAP_WINDOW_MS = 650L
    }
}
