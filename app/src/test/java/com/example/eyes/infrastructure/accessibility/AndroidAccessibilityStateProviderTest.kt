package com.example.eyes.infrastructure.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidAccessibilityStateProviderTest {
    @Test
    fun screenReaderLikelyRequiresAccessibilityAndTouchExploration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val shadow = shadowOf(manager)
        val provider = AndroidAccessibilityStateProvider(context)

        shadow.setEnabled(false)
        shadow.setTouchExplorationEnabled(false)
        assertFalse(provider.isTouchExplorationEnabled)
        assertFalse(provider.isScreenReaderLikelyEnabled)

        shadow.setEnabled(true)
        assertFalse(provider.isScreenReaderLikelyEnabled)

        shadow.setTouchExplorationEnabled(true)
        assertTrue(provider.isTouchExplorationEnabled)
        assertTrue(provider.isScreenReaderLikelyEnabled)
    }

    @Test
    fun screenReaderLikelyEnabledFlowEmitsChangesAndRemovesListeners() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val shadow = shadowOf(manager)
        val provider = AndroidAccessibilityStateProvider(context)
        val values = mutableListOf<Boolean>()

        shadow.setEnabled(false)
        shadow.setTouchExplorationEnabled(false)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.screenReaderLikelyEnabledFlow.toList(values) }
        shadow.setEnabled(true)
        shadow.setTouchExplorationEnabled(true)
        shadow.setTouchExplorationEnabled(false)
        job.cancelAndJoin()

        assertTrue(values.contains(false))
        assertTrue(values.contains(true))
    }
}
