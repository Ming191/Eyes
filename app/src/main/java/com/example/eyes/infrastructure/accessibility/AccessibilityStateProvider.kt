package com.example.eyes.infrastructure.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager

interface AccessibilityStateProvider {
    val isTouchExplorationEnabled: Boolean
    val isScreenReaderLikelyEnabled: Boolean
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
}
