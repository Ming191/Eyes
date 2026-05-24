package com.example.eyes.ui.blind

import com.example.eyes.system.SpeechOutput
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BlindScrollManager(
    private val speechOutput: SpeechOutput,
    private val coroutineScope: CoroutineScope,
    private val localeProvider: () -> Locale?,
    private val scrollForwardLabelProvider: () -> String,
    private val scrollBackwardLabelProvider: () -> String,
    private val scrollEndLabelProvider: () -> String,
    private val scrollStartLabelProvider: () -> String
) {
    private val targets = mutableListOf<BlindScrollTarget>()

    fun registerOrUpdate(target: BlindScrollTarget) {
        val index = targets.indexOfFirst { it.id == target.id }
        if (index >= 0) {
            targets[index] = target
        } else {
            targets.add(target)
        }
    }

    fun unregister(id: String) {
        targets.removeAll { it.id == id }
    }

    fun scrollForward() {
        val target = targets.asReversed().firstOrNull { it.canScrollForward() }
        if (target == null) {
            speak(scrollEndLabelProvider())
            return
        }

        coroutineScope.launch { target.scrollForward() }
        speak(scrollForwardLabelProvider())
    }

    fun scrollBackward() {
        val target = targets.asReversed().firstOrNull { it.canScrollBackward() }
        if (target == null) {
            speak(scrollStartLabelProvider())
            return
        }

        coroutineScope.launch { target.scrollBackward() }
        speak(scrollBackwardLabelProvider())
    }

    private fun speak(text: String) {
        speechOutput.stop()
        speechOutput.speak(text, SpeechOutput.Priority.NORMAL, localeProvider())
    }
}

data class BlindScrollTarget(
    val id: String,
    val canScrollForward: () -> Boolean,
    val canScrollBackward: () -> Boolean,
    val scrollForward: suspend () -> Unit,
    val scrollBackward: suspend () -> Unit
)
