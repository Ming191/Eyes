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

    var focusedBounds by mutableStateOf<Rect?>(null)
        private set

    fun registerOrUpdate(item: BlindFocusItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            items[index] = item
        } else {
            items.add(item)
            sortItems()
        }
    }

    fun unregister(id: String) {
        val removedIndex = items.indexOfFirst { it.id == id }
        if (removedIndex < 0) return

        items.removeAt(removedIndex)
        if (items.isEmpty()) {
            focusedIndex = -1
            selectedActionIndex = -1
            focusedBounds = null
            lastSpokenItemId = null
        } else {
            focusedIndex = focusedIndex.coerceIn(0, items.lastIndex)
            updateFocusedBounds()
        }
    }

    fun focusNext() {
        if (items.isEmpty()) return
        focusedIndex = if (focusedIndex < items.lastIndex) focusedIndex + 1 else 0
        selectedActionIndex = -1
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusPrevious() {
        if (items.isEmpty()) return
        focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else items.lastIndex
        selectedActionIndex = -1
        updateFocusedBounds()
        speakFocused(force = true)
    }

    fun focusNextAction() {
        val item = items.getOrNull(focusedIndex) ?: return
        if (item.actions.isEmpty()) {
            speakInterrupting(noActionsLabelProvider(), SpeechOutput.Priority.NORMAL)
            return
        }

        selectedActionIndex = if (selectedActionIndex < item.actions.lastIndex) selectedActionIndex + 1 else 0
        speakInterrupting(item.actions[selectedActionIndex].label, SpeechOutput.Priority.NORMAL)
    }

    fun focusPreviousAction() {
        val item = items.getOrNull(focusedIndex) ?: return
        if (item.actions.isEmpty()) {
            speakInterrupting(noActionsLabelProvider(), SpeechOutput.Priority.NORMAL)
            return
        }

        selectedActionIndex = if (selectedActionIndex > 0) selectedActionIndex - 1 else item.actions.lastIndex
        speakInterrupting(item.actions[selectedActionIndex].label, SpeechOutput.Priority.NORMAL)
    }

    fun activateFocused() {
        val item = items.getOrNull(focusedIndex) ?: return
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
        val index = items.indexOfFirst { it.bounds.contains(position) }
        if (index < 0 || index == focusedIndex) return

        focusedIndex = index
        selectedActionIndex = -1
        updateFocusedBounds()
        speakFocused(force = false)
    }

    private fun speakFocused(force: Boolean) {
        val item = items.getOrNull(focusedIndex) ?: return
        if (!force && item.id == lastSpokenItemId) return

        lastSpokenItemId = item.id
        speakInterrupting(item.label, SpeechOutput.Priority.NORMAL)
    }

    private fun sortItems() {
        items.sortWith(compareBy<BlindFocusItem> { it.bounds.top }.thenBy { it.bounds.left })
        focusedIndex = focusedIndex.coerceIn(-1, items.lastIndex)
        updateFocusedBounds()
    }

    private fun updateFocusedBounds() {
        focusedBounds = items.getOrNull(focusedIndex)?.bounds
    }

    private fun speakInterrupting(text: String, priority: SpeechOutput.Priority) {
        speechOutput.stop()
        speechOutput.speak(text, priority, localeProvider())
    }
}

data class BlindAction(
    val label: String,
    val onActivate: () -> Unit,
    val activateLabel: String? = null
)

data class BlindFocusItem(
    val id: String,
    val label: String,
    val bounds: Rect,
    val onActivate: () -> Unit,
    val activateLabel: String? = null,
    val actions: List<BlindAction> = emptyList()
)
