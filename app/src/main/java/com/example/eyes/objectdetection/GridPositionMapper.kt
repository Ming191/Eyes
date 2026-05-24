package com.example.eyes.objectdetection

import android.graphics.RectF

class GridPositionMapper {

    fun map(
        boundingBox: RectF,
        frameWidth: Int,
        frameHeight: Int
    ): DetectionPosition {
        require(frameWidth > 0) { "frameWidth must be positive" }
        require(frameHeight > 0) { "frameHeight must be positive" }

        val centerX = boundingBox.centerX().coerceIn(0f, frameWidth.toFloat())
        val centerY = boundingBox.centerY().coerceIn(0f, frameHeight.toFloat())

        val column = when {
            centerX < frameWidth / 3f -> Column.LEFT
            centerX < frameWidth * 2f / 3f -> Column.CENTER
            else -> Column.RIGHT
        }
        val row = when {
            centerY < frameHeight / 3f -> Row.TOP
            centerY < frameHeight * 2f / 3f -> Row.CENTER
            else -> Row.BOTTOM
        }

        return when (row to column) {
            Row.TOP to Column.LEFT -> DetectionPosition.TOP_LEFT
            Row.TOP to Column.CENTER -> DetectionPosition.TOP_CENTER
            Row.TOP to Column.RIGHT -> DetectionPosition.TOP_RIGHT
            Row.CENTER to Column.LEFT -> DetectionPosition.CENTER_LEFT
            Row.CENTER to Column.CENTER -> DetectionPosition.CENTER
            Row.CENTER to Column.RIGHT -> DetectionPosition.CENTER_RIGHT
            Row.BOTTOM to Column.LEFT -> DetectionPosition.BOTTOM_LEFT
            Row.BOTTOM to Column.CENTER -> DetectionPosition.BOTTOM_CENTER
            Row.BOTTOM to Column.RIGHT -> DetectionPosition.BOTTOM_RIGHT
            else -> error("Unsupported grid cell")
        }
    }

    private enum class Row { TOP, CENTER, BOTTOM }
    private enum class Column { LEFT, CENTER, RIGHT }
}
