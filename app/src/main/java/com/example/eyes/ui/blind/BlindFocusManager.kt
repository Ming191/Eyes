package com.example.eyes.ui.blind

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.eyes.system.SpeechOutput
import java.util.Locale

class BlindFocusManager(
    private val speechOutput: SpeechOutput,
    private val localeProvider: () -> Locale?,
    private val noActionsLabelProvider: () -> String
) {
    private val items = mutableStateListOf<BlindFocusItem>()
    private var focusedIndex = -1
    private var selectedActionIndex = -1
    private var lastSpokenItemId: String? = null
    private var activeRouteKey = GLOBAL_ROUTE_KEY

    var focusedBounds by mutableStateOf<Rect?>(null)
        private set

    fun registerOrUpdate(item: BlindFocusItem) {
        val index = items.indexOfFirst { it.id == item.id && it.routeKey == item.routeKey }
        if (index >= 0) {
            items[index] = item
        } else {
            items.add(item)
        }
        sortItems()
    }

    fun unregister(id: String, routeKey: String) {
        val removedIndex = items.indexOfFirst { it.id == id && it.routeKey == routeKey }
        if (removedIndex < 0) return

        items.removeAt(removedIndex)
        focusedIndex = focusedIndex.coerceIn(-1, activeItems().lastIndex)
        updateFocusedBounds()
    }

    fun setActiveRoute(routeKey: String) {
        if (activeRouteKey == routeKey) return

        activeRouteKey = routeKey
        focusedIndex = -1
        selectedActionIndex = -1
        focusedBounds = null
        lastSpokenItemId = null
    }

    fun focusItem(id: String, routeKey: String = GLOBAL_ROUTE_KEY) {
        val index = activeItems().indexOfFirst { it.id == id && it.routeKey == routeKey }
        if (index < 0) return

        focusedIndex = index
        selectedActionIndex = -1
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusNext() {
        val visibleItems = activeItems()
        if (visibleItems.isEmpty()) return
        focusedIndex = if (focusedIndex < visibleItems.lastIndex) focusedIndex + 1 else 0
        selectedActionIndex = -1
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusPrevious() {
        val visibleItems = activeItems()
        if (visibleItems.isEmpty()) return
        focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else visibleItems.lastIndex
        selectedActionIndex = -1
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusNextAction() {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        if (item.actions.isEmpty()) {
            speakInterrupting(noActionsLabelProvider(), SpeechOutput.Priority.NORMAL)
            return
        }

        selectedActionIndex = if (selectedActionIndex < item.actions.lastIndex) selectedActionIndex + 1 else 0
        speakInterrupting(item.actions[selectedActionIndex].label, SpeechOutput.Priority.NORMAL)
    }

    fun focusPreviousAction() {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        if (item.actions.isEmpty()) {
            speakInterrupting(noActionsLabelProvider(), SpeechOutput.Priority.NORMAL)
            return
        }

        selectedActionIndex = if (selectedActionIndex > 0) selectedActionIndex - 1 else item.actions.lastIndex
        speakInterrupting(item.actions[selectedActionIndex].label, SpeechOutput.Priority.NORMAL)
    }

    fun activateFocused() {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        val action = item.actions.getOrNull(selectedActionIndex)
        if (action != null) {
            speakInterrupting(action.activateLabel ?: action.label, SpeechOutput.Priority.HIGH)
            action.onActivate()
            selectedActionIndex = -1
        } else {
            speakInterrupting(item.activateLabel ?: item.label, SpeechOutput.Priority.HIGH)
            item.onActivate()
        }
    }

    fun focusAt(position: Offset) {
        val index = activeItems().indexOfFirst { it.bounds.contains(position) }
        if (index < 0 || index == focusedIndex) return

        focusedIndex = index
        selectedActionIndex = -1
        updateFocusedBounds()
        speakFocused(force = false)
    }

    private fun speakFocused(force: Boolean) {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        if (!force && item.id == lastSpokenItemId) return

        lastSpokenItemId = item.id
        speakInterrupting(item.label, SpeechOutput.Priority.NORMAL)
    }

    private fun sortItems() {
        items.sortWith(compareBy<BlindFocusItem> { it.bounds.top }.thenBy { it.bounds.left })
        focusedIndex = focusedIndex.coerceIn(-1, activeItems().lastIndex)
        updateFocusedBounds()
    }

    private fun updateFocusedBounds() {
        focusedBounds = activeItems().getOrNull(focusedIndex)?.bounds
    }

    private fun activeItems(): List<BlindFocusItem> {
        return items.filter { it.routeKey == GLOBAL_ROUTE_KEY || it.routeKey == activeRouteKey }
    }

    private fun speakInterrupting(text: String, priority: SpeechOutput.Priority) {
        speechOutput.stop()
        speechOutput.speak(text, priority, localeProvider())
    }

    companion object {
        const val GLOBAL_ROUTE_KEY = "global"
    }
}

data class BlindAction(
    val label: String,
    val onActivate: () -> Unit,
    val activateLabel: String? = null
)

data class BlindFocusItem(
    val id: String,
    val routeKey: String,
    val label: String,
    val bounds: Rect,
    val onActivate: () -> Unit,
    val activateLabel: String? = null,
    val actions: List<BlindAction> = emptyList()
)
