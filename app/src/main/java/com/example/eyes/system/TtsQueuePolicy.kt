package com.example.eyes.system

internal enum class TtsQueueMode {
    Flush,
    Add
}

internal data class TtsQueuedItem(
    val priority: SpeechOutput.Priority,
    val sequence: Long
)

internal object TtsQueuePolicy {
    fun queueModeFor(priority: SpeechOutput.Priority): TtsQueueMode = when (priority) {
        SpeechOutput.Priority.URGENT -> TtsQueueMode.Flush
        SpeechOutput.Priority.HIGH,
        SpeechOutput.Priority.NORMAL -> TtsQueueMode.Add
    }

    fun nextIndex(items: List<TtsQueuedItem>): Int? {
        if (items.isEmpty()) return null
        return items.withIndex()
            .minWithOrNull(
                compareBy<IndexedValue<TtsQueuedItem>>(
                    { it.value.priority.rank() },
                    { it.value.sequence }
                )
            )
            ?.index
    }

    private fun SpeechOutput.Priority.rank(): Int = when (this) {
        SpeechOutput.Priority.URGENT -> 0
        SpeechOutput.Priority.HIGH -> 1
        SpeechOutput.Priority.NORMAL -> 2
    }
}
