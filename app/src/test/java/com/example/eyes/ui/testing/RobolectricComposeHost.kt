package com.example.eyes.ui.testing

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

class RobolectricComposeHost {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity

    fun start() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = controller.get()
    }

    fun setContent(rule: ComposeTestRule, content: @Composable () -> Unit) {
        rule.runOnUiThread {
            activity.setContent(content = content)
        }
        rule.waitForIdle()
    }

    fun dispose() {
        if (::controller.isInitialized) {
            controller.pause().stop().destroy()
        }
    }
}
