package com.example.eyes

import android.os.Bundle
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
import com.example.eyes.ui.navigation.AppNavGraph
import com.example.eyes.ui.theme.EyesTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val dataStoreManager: DataStoreManager by inject()

    /**
     * Initializes the activity, enables edge-to-edge display, and sets the Jetpack Compose UI.
     *
     * @param savedInstanceState A [Bundle] containing the activity's previously saved state, or `null` if none.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by dataStoreManager.appLanguageFlow.collectAsStateWithLifecycle(initialValue = com.example.eyes.domain.i18n.AppLanguage.VI)
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

}
