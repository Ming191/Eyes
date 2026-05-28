package com.example.eyes.infrastructure.i18n

import android.content.Context
import com.example.eyes.R
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.objectdetection.DetectionPosition
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.i18n.localizedFor

fun DetectionPosition.localizedText(
    context: Context,
    language: AppLanguage
): String = context.localizedFor(language).getString(labelRes)

fun DetectionPosition.localizedText(
    localizedTextProvider: LocalizedTextProvider,
    language: AppLanguage
): String = localizedTextProvider.getString(labelRes, language)

private val DetectionPosition.labelRes: Int
    get() = when (this) {
        DetectionPosition.TOP_LEFT -> R.string.object_detection_position_top_left
        DetectionPosition.TOP_CENTER -> R.string.object_detection_position_top_center
        DetectionPosition.TOP_RIGHT -> R.string.object_detection_position_top_right
        DetectionPosition.CENTER_LEFT -> R.string.object_detection_position_center_left
        DetectionPosition.CENTER -> R.string.object_detection_position_center
        DetectionPosition.CENTER_RIGHT -> R.string.object_detection_position_center_right
        DetectionPosition.BOTTOM_LEFT -> R.string.object_detection_position_bottom_left
        DetectionPosition.BOTTOM_CENTER -> R.string.object_detection_position_bottom_center
        DetectionPosition.BOTTOM_RIGHT -> R.string.object_detection_position_bottom_right
    }
