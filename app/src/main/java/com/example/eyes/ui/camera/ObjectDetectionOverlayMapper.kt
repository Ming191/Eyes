package com.example.eyes.ui.camera

import com.example.eyes.R
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.objectdetection.Detection
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider

fun Detection.toOverlayItem(
    frameWidth: Int,
    frameHeight: Int,
    localizedTextProvider: LocalizedTextProvider,
    language: AppLanguage
): DetectionOverlayItem {
    val box = boundingBox
    return DetectionOverlayItem(
        label = localizedObjectDetectionLabel(classId, label, localizedTextProvider, language),
        confidence = confidence,
        positionText = position.localizedText(localizedTextProvider, language),
        left = box.left / frameWidth,
        top = box.top / frameHeight,
        right = box.right / frameWidth,
        bottom = box.bottom / frameHeight,
        sourceAspectRatio = frameWidth.toFloat() / frameHeight.toFloat()
    )
}

private fun localizedObjectDetectionLabel(
    classId: Int,
    fallback: String,
    localizedTextProvider: LocalizedTextProvider,
    language: AppLanguage
): String {
    val labels = localizedTextProvider.getStringArray(R.array.object_detection_coco_labels, language)
    return labels.getOrNull(classId) ?: fallback
}
