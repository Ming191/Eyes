package com.example.eyes.infrastructure.objectdetection

import android.graphics.RectF
import com.example.eyes.objectdetection.Detection
import kotlin.math.max
import kotlin.math.min

class YoloPostprocessor(
    private val inputSize: Int = YoloExecutorchDetector.DEFAULT_INPUT_SIZE,
    private val gridPositionMapper: GridPositionMapper = GridPositionMapper(),
    private val labels: List<String> = YoloModelMetadata.cocoLabelKeys,
    private val confidenceThreshold: Float = 0.60f,
    private val iouThreshold: Float = 0.45f,
    private val maxDetections: Int = 8
) {

    private val classCount = labels.size
    private val outputChannels = BOX_CHANNELS + classCount

    fun postprocess(
        output: FloatArray,
        frameWidth: Int,
        frameHeight: Int
    ): List<Detection> {
        val candidates = output.size / outputChannels
        val rawDetections = buildList {
            for (candidate in 0 until candidates) {
                var bestClassId = -1
                var bestScore = 0f
                for (classId in labels.indices) {
                    val score = output[(BOX_CHANNELS + classId) * candidates + candidate]
                    if (score > bestScore) {
                        bestScore = score
                        bestClassId = classId
                    }
                }
                if (bestScore < confidenceThreshold || bestClassId < 0) continue

                val centerX = output[candidate] / inputSize * frameWidth
                val centerY = output[candidates + candidate] / inputSize * frameHeight
                val width = output[candidates * 2 + candidate] / inputSize * frameWidth
                val height = output[candidates * 3 + candidate] / inputSize * frameHeight
                val box = RectF(
                    (centerX - width / 2f).coerceIn(0f, frameWidth.toFloat()),
                    (centerY - height / 2f).coerceIn(0f, frameHeight.toFloat()),
                    (centerX + width / 2f).coerceIn(0f, frameWidth.toFloat()),
                    (centerY + height / 2f).coerceIn(0f, frameHeight.toFloat())
                )
                if (box.width() <= 1f || box.height() <= 1f) continue
                add(
                    Detection(
                        classId = bestClassId,
                        label = labels[bestClassId],
                        confidence = bestScore,
                        boundingBox = box,
                        position = gridPositionMapper.map(box, frameWidth, frameHeight)
                    )
                )
            }
        }.sortedByDescending { it.confidence }

        return nonMaxSuppression(rawDetections).take(maxDetections)
    }

    private fun nonMaxSuppression(detections: List<Detection>): List<Detection> {
        val selected = mutableListOf<Detection>()
        for (detection in detections) {
            val overlaps = selected.any { existing ->
                existing.classId == detection.classId && iou(existing.boundingBox, detection.boundingBox) > iouThreshold
            }
            if (!overlaps) selected += detection
        }
        return selected
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private companion object {
        private const val BOX_CHANNELS = 4
    }
}
