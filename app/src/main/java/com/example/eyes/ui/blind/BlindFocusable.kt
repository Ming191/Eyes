package com.example.eyes.ui.blind

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    actions: List<BlindAction> = emptyList()
): Modifier = composed {
    val manager = LocalBlindFocusManager.current
    val routeKey = LocalBlindFocusRouteKey.current
    var bounds by remember { mutableStateOf(Rect.Zero) }

    DisposableEffect(manager, id, routeKey, label, bounds, onActivate, activateLabel, actions) {
        manager?.registerOrUpdate(
            BlindFocusItem(
                id = id,
                routeKey = routeKey,
                label = label,
                bounds = bounds,
                onActivate = onActivate,
                activateLabel = activateLabel,
                actions = actions
            )
        )

        onDispose {
            manager?.unregister(id, routeKey)
        }
    }

    onGloballyPositioned { coordinates ->
        bounds = coordinates.boundsInRoot()
    }
}
