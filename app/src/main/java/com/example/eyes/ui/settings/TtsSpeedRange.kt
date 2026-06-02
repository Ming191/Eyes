package com.example.eyes.ui.settings

import kotlin.math.roundToInt

internal const val TTS_SPEED_MIN = 0.25f
internal const val TTS_SPEED_MAX = 2.0f
internal const val TTS_SPEED_STEP = 0.05f
internal const val TTS_SPEED_SLIDER_STEPS = 34

internal val TTS_SPEED_RANGE = TTS_SPEED_MIN..TTS_SPEED_MAX

internal data class TtsSpeedPresetValue(
    val idSuffix: String,
    val speed: Float,
    val label: String
)

internal val TTS_SPEED_PRESET_VALUES = listOf(
    TtsSpeedPresetValue("050", 0.5f, "0.5"),
    TtsSpeedPresetValue("075", 0.75f, "0.75"),
    TtsSpeedPresetValue("100", 1.0f, "1.00"),
    TtsSpeedPresetValue("125", 1.25f, "1.25"),
    TtsSpeedPresetValue("150", 1.5f, "1.5")
)

internal fun Float.snapToTtsSpeedStep(): Float {
    val minHundredths = (TTS_SPEED_MIN * 100).roundToInt()
    val maxHundredths = (TTS_SPEED_MAX * 100).roundToInt()
    val stepHundredths = (TTS_SPEED_STEP * 100).roundToInt()
    val boundedHundredths = (this * 100).roundToInt().coerceIn(minHundredths, maxHundredths)
    val stepIndex = ((boundedHundredths - minHundredths).toFloat() / stepHundredths).roundToInt()
    return (minHundredths + stepIndex * stepHundredths) / 100f
}

internal fun Float.previousTtsSpeedStep(): Float = (snapToTtsSpeedStep() - TTS_SPEED_STEP).snapToTtsSpeedStep()

internal fun Float.nextTtsSpeedStep(): Float = (snapToTtsSpeedStep() + TTS_SPEED_STEP).snapToTtsSpeedStep()
