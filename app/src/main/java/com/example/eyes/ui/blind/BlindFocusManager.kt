package com.example.eyes.ui.blind

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.eyes.infrastructure.system.SpeechOutput
import java.util.Locale

class BlindFocusManager(
    private val speechOutput: SpeechOutput,
    private val localeProvider: () -> Locale?,
    private val noActionsLabelProvider: () -> String
) {
    private val items = mutableStateListOf<BlindFocusItem>()
    private var focusedIndex = -1
    private var focusedItemKey: FocusItemKey? = null
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
        if (focusedItemKey == FocusItemKey(item.id, item.routeKey)) {
            focusedIndex = activeItems().indexOfFirst { it.key == focusedItemKey }
            updateFocusedBounds()
        }
    }

    fun unregister(id: String, routeKey: String) {
        val removedIndex = items.indexOfFirst { it.id == id && it.routeKey == routeKey }
        if (removedIndex < 0) return

        items.removeAt(removedIndex)
        if (focusedItemKey == FocusItemKey(id, routeKey)) {
            clearFocus()
            return
        }
        focusedIndex = focusedIndex.coerceIn(-1, activeItems().lastIndex)
        updateFocusedBounds()
    }

    fun setActiveRoute(routeKey: String) {
        if (activeRouteKey == routeKey) return

        activeRouteKey = routeKey
        clearFocus()
    }

    fun focusItem(id: String, routeKey: String = GLOBAL_ROUTE_KEY) {
        val index = activeItems().indexOfFirst { it.id == id && it.routeKey == routeKey }
        if (index < 0) return

        setFocus(index)
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusNext() {
        val visibleItems = activeItems()
        if (visibleItems.isEmpty()) return
        setFocus(if (focusedIndex < visibleItems.lastIndex) focusedIndex + 1 else 0)
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusPrevious() {
        val visibleItems = activeItems()
        if (visibleItems.isEmpty()) return
        setFocus(if (focusedIndex > 0) focusedIndex - 1 else visibleItems.lastIndex)
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusNextAction() {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        if (item.actions.isEmpty()) {
            speakInterrupting(noActionsLabelProvider())
            return
        }

        selectedActionIndex = if (selectedActionIndex < item.actions.lastIndex) selectedActionIndex + 1 else 0
        speakInterrupting(item.actions[selectedActionIndex].label)
    }

    fun focusPreviousAction() {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        if (item.actions.isEmpty()) {
            speakInterrupting(noActionsLabelProvider())
            return
        }

        selectedActionIndex = if (selectedActionIndex > 0) selectedActionIndex - 1 else item.actions.lastIndex
        speakInterrupting(item.actions[selectedActionIndex].label)
    }

    fun activateFocused() {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        val action = item.actions.getOrNull(selectedActionIndex)
        if (action != null) {
            speakInterrupting(action.activateLabel ?: action.label)
            action.onActivate()
            selectedActionIndex = -1
        } else {
            speakInterrupting(item.activateLabel ?: item.label)
            item.onActivate()
        }
    }

    fun focusAt(position: Offset) {
        val visibleItems = activeItems()
        val index = visibleItems
            .withIndex()
            .filter { it.value.bounds.contains(position) }
            .minByOrNull { it.value.bounds.width * it.value.bounds.height }
            ?.index
            ?: -1
        if (index < 0 || index == focusedIndex) return

        setFocus(index)
        updateFocusedBounds()
        speakFocused(force = false)
    }

    private fun speakFocused(force: Boolean) {
        val item = activeItems().getOrNull(focusedIndex) ?: return
        if (!force && item.id == lastSpokenItemId) return

        lastSpokenItemId = item.id
        speakInterrupting(item.label)
    }

    private fun sortItems() {
        items.sortWith(compareBy<BlindFocusItem> { it.bounds.top }.thenBy { it.bounds.left })
        focusedIndex = focusedItemKey?.let { key ->
            activeItems().indexOfFirst { it.key == key }
        } ?: -1
        updateFocusedBounds()
    }

    private fun setFocus(index: Int) {
        val item = activeItems().getOrNull(index)
        focusedIndex = if (item == null) -1 else index
        focusedItemKey = item?.key
        selectedActionIndex = -1
    }

    private fun clearFocus() {
        focusedIndex = -1
        focusedItemKey = null
        selectedActionIndex = -1
        focusedBounds = null
        lastSpokenItemId = null
    }

    private fun updateFocusedBounds() {
        focusedBounds = activeItems().getOrNull(focusedIndex)?.bounds
    }

    private fun activeItems(): List<BlindFocusItem> {
        return items.filter { it.routeKey == GLOBAL_ROUTE_KEY || it.routeKey == activeRouteKey }
    }

    private fun speakInterrupting(text: String) {
        speechOutput.stop()
        localeProvider()?.let { speechOutput.speak(text, it) } ?: speechOutput.speak(text)
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
) {
    val key: FocusItemKey
        get() = FocusItemKey(id = id, routeKey = routeKey)
}

data class FocusItemKey(
    val id: String,
    val routeKey: String
)
