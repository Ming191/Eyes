package com.example.eyes.objectdetection

import androidx.annotation.StringRes
import com.example.eyes.R

enum class DetectionPosition(
    @param:StringRes val labelRes: Int
) {
    TOP_LEFT(R.string.object_detection_position_top_left),
    TOP_CENTER(R.string.object_detection_position_top_center),
    TOP_RIGHT(R.string.object_detection_position_top_right),
    CENTER_LEFT(R.string.object_detection_position_center_left),
    CENTER(R.string.object_detection_position_center),
    CENTER_RIGHT(R.string.object_detection_position_center_right),
    BOTTOM_LEFT(R.string.object_detection_position_bottom_left),
    BOTTOM_CENTER(R.string.object_detection_position_bottom_center),
    BOTTOM_RIGHT(R.string.object_detection_position_bottom_right)
}
