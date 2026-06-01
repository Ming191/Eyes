package com.example.eyes.ui.blind

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

fun Modifier.blindFocusable(
    id: String,
    label: String,
    onActivate: () -> Unit,
    activateLabel: String? = null,
    actions: List<BlindAction> = emptyList(),
    adjustment: BlindDragAdjustment? = null
): Modifier = composed {
    val manager = LocalBlindFocusManager.current
    val routeKey = LocalBlindFocusRouteKey.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentLabel by rememberUpdatedState(label)
    val currentBounds by rememberUpdatedState(bounds)
    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentActivateLabel by rememberUpdatedState(activateLabel)
    val currentActions by rememberUpdatedState(actions)
    val currentAdjustment by rememberUpdatedState(adjustment)

    SideEffect {
        manager?.registerOrUpdate(
            BlindFocusItem(
                id = id,
                routeKey = routeKey,
                label = currentLabel,
                bounds = currentBounds,
                onActivate = currentOnActivate,
                activateLabel = currentActivateLabel,
                actions = currentActions,
                adjustment = currentAdjustment
            )
        )
    }

    DisposableEffect(manager, id, routeKey) {
        onDispose {
            manager?.unregister(id, routeKey)
        }
    }

    onGloballyPositioned { coordinates ->
        bounds = coordinates.boundsInRoot()
    }
}
