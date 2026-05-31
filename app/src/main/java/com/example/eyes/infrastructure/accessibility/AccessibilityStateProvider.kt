package com.example.eyes.infrastructure.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface AccessibilityStateProvider {
    val isTouchExplorationEnabled: Boolean
    val isScreenReaderLikelyEnabled: Boolean
    val screenReaderLikelyEnabledFlow: Flow<Boolean>
}

class AndroidAccessibilityStateProvider(
    context: Context
) : AccessibilityStateProvider {
    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    override val isTouchExplorationEnabled: Boolean
        get() = accessibilityManager.isTouchExplorationEnabled

    override val isScreenReaderLikelyEnabled: Boolean
        get() = accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled

    override val screenReaderLikelyEnabledFlow: Flow<Boolean> = callbackFlow {
        fun sendCurrent() {
            trySend(isScreenReaderLikelyEnabled)
        }

        val accessibilityListener = AccessibilityManager.AccessibilityStateChangeListener { sendCurrent() }
        val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { sendCurrent() }

        sendCurrent()
        accessibilityManager.addAccessibilityStateChangeListener(accessibilityListener)
        accessibilityManager.addTouchExplorationStateChangeListener(touchExplorationListener)

        awaitClose {
            accessibilityManager.removeAccessibilityStateChangeListener(accessibilityListener)
            accessibilityManager.removeTouchExplorationStateChangeListener(touchExplorationListener)
        }
    }.distinctUntilChanged()
}
