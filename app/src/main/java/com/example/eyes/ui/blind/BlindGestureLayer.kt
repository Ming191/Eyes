package com.example.eyes.ui.blind

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.eyes.infrastructure.system.SpeechOutput
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

val LocalBlindFocusManager = compositionLocalOf<BlindFocusManager?> { null }
val LocalBlindFocusRouteKey = compositionLocalOf { BlindFocusManager.GLOBAL_ROUTE_KEY }

@Composable
fun BlindGestureLayer(
    speechOutput: SpeechOutput,
    localeProvider: () -> java.util.Locale? = { null },
    noActionsLabel: String,
    layerDescription: String,
    focusOverlayDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val currentLocaleProvider = rememberUpdatedState(localeProvider)
    val currentNoActionsLabel = rememberUpdatedState(noActionsLabel)
    val manager = remember(speechOutput) {
        BlindFocusManager(
            speechOutput = speechOutput,
            localeProvider = { currentLocaleProvider.value() },
            noActionsLabelProvider = { currentNoActionsLabel.value }
        )
    }
    val viewConfiguration = LocalViewConfiguration.current

    CompositionLocalProvider(LocalBlindFocusManager provides manager) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .semantics { contentDescription = layerDescription }
                .pointerInput(manager, viewConfiguration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        down.consume()
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                        val swipeThreshold = viewConfiguration.touchSlop * 6f

                        val longPressChange = withTimeoutOrNull(longPressTimeout) {
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        }

                        if (longPressChange == null) {
                            handleExploreByTouch(manager)
                            return@awaitEachGesture
                        }

                        val delta = longPressChange.position - down.position
                        if (abs(delta.x) > abs(delta.y) && abs(delta.x) > swipeThreshold) {
                            if (delta.x > 0) manager.focusNext() else manager.focusPrevious()
                            return@awaitEachGesture
                        }

                        if (abs(delta.y) > abs(delta.x) && abs(delta.y) > swipeThreshold) {
                            if (delta.y > 0) manager.focusNextAction() else manager.focusPreviousAction()
                            return@awaitEachGesture
                        }

                        val secondDown = withTimeoutOrNull(doubleTapTimeout) {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            ).also { it.consume() }
                        }
                        if (secondDown != null) {
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            manager.activateFocused()
                        }
                    }
                },
        ) {
            content()
            FocusBoundsOverlay(
                manager = manager,
                description = focusOverlayDescription
            )
        }
    }
}

@Composable
private fun FocusBoundsOverlay(
    manager: BlindFocusManager,
    description: String
) {
    val bounds = manager.focusedBounds ?: return
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    ) {
        drawRect(
            color = Color(0xFF00E676),
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
            style = Stroke(width = 5f)
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleExploreByTouch(
    manager: BlindFocusManager
) {
    while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        if (event.changes.size > 1) break
        val activeChange = event.changes.firstOrNull { it.pressed } ?: break
        event.changes.forEach { it.consume() }
        manager.focusAt(activeChange.position)
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancellation(
    pass: PointerEventPass
): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(pass = pass)
        if (event.changes.size > 1) return null
        event.changes.forEach { it.consume() }
        val activeChange = event.changes.firstOrNull()
        if (event.changes.all { !it.pressed }) return activeChange
    }
}
