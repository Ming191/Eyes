package com.example.eyes

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eyes.data.DataStoreManager
import com.example.eyes.i18n.localizedFor
import com.example.eyes.service.ObstacleDetectionService
import com.example.eyes.ui.navigation.AppNavGraph
import com.example.eyes.ui.theme.EyesTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val dataStoreManager: DataStoreManager by inject()
    private var lastVolumeDownTapAt: Long = 0L

    /**
     * Initializes the activity, enables edge-to-edge display, and sets the Jetpack Compose UI.
     *
     * @param savedInstanceState A [Bundle] containing the activity's previously saved state, or `null` if none.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by dataStoreManager.appLanguageFlow.collectAsStateWithLifecycle(initialValue = com.example.eyes.i18n.AppLanguage.VI)
            val baseContext = LocalContext.current
            val activityResultRegistryOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
            val configuration = LocalConfiguration.current
            val localizedContext = remember(baseContext, configuration, language) {
                baseContext.localizedFor(language)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides activityResultRegistryOwner
            ) {
                EyesTheme {
                    AppNavGraph()
                }
            }
        }
    }

    /**
     * Intercepts volume-down key events to detect a double-tap and toggle obstacle detection.
     *
     * @param event The key event to dispatch; when a volume-down double-tap is detected the event is consumed.
     * @return `true` if a volume-down double-tap was detected and consumed, `false` otherwise.
     */
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
