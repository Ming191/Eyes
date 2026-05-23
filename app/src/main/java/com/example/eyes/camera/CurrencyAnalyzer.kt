package com.example.eyes.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList

/**
 * Nhận diện mệnh giá tiền Việt Nam - 2 stage pipeline:
 *   Stage 1: YOLOv8 (best.tflite) → phát hiện vùng tờ tiền
 *   Stage 2: EfficientNet-B0 (EfficientNet_float32.tflite) → phân loại mệnh giá
 *
 * Files cần có trong assets/:
 *   best.tflite
 *   EfficientNet_float32.tflite
 *   currency_labels.txt
 */
class CurrencyAnalyzer(
    context: Context,
    private val onResult: (label: String, confidence: Float) -> Unit
) {
    // ── Interpreters ──────────────────────────────────────────────
    private val yoloInterp: Interpreter
    private val clsInterp: Interpreter
    private val labels: List<String>

    // ── Bitmap reuse ──────────────────────────────────────────────
    private val yoloBmp = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
    private val clsBmp  = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
    private val yoloCvs = Canvas(yoloBmp)
    private val clsCvs  = Canvas(clsBmp)

    // ── ByteBuffer reuse ──────────────────────────────────────────
    private val yoloBuf = ByteBuffer
        .allocateDirect(640 * 640 * 3 * 4)
        .order(ByteOrder.nativeOrder())
    private val clsBuf = ByteBuffer
        .allocateDirect(224 * 224 * 3 * 4)
        .order(ByteOrder.nativeOrder())
    private val yoloPixels = IntArray(640 * 640)
    private val clsPixels = IntArray(224 * 224)

    // ── Frame averaging ───────────────────────────────────────────
    private val frameWindow   = LinkedList<Map<String, Float>>()
    private val WINDOW_SIZE   = 5
    private val STABLE_FRAMES = 3


    // ── Label maps ────────────────────────────────────────────────
    companion object {
        const val EMPTY_LABEL = ""
        private const val YOLO_CONF_THRESHOLD     = 0.40f
        private const val CLASSIFY_CONF_THRESHOLD = 0.70f
    }
    init {
        val opts = Interpreter.Options().apply { numThreads = 4 }
        yoloInterp = Interpreter(FileUtil.loadMappedFile(context, "best.tflite"), opts)
        clsInterp  = Interpreter(FileUtil.loadMappedFile(context, "EfficientNet_float32.tflite"), opts)
        labels     = FileUtil.loadLabels(context, "currency_labels.txt")
    }

    // ═════════════════════════════════════════════════════════════
    // PUBLIC — gọi từ CameraViewModel
    // ═════════════════════════════════════════════════════════════

    fun analyze(imageProxy: ImageProxy) {
        var bitmap: Bitmap? = null
        try {
            bitmap = imageProxy.toBitmapWithRotation()
            processFrame(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(EMPTY_LABEL, 0f)
        } finally {
            imageProxy.close()
            bitmap?.recycle()
        }
    }

    fun resetBuffer() = frameWindow.clear()

    fun close() {
        yoloInterp.close()
        clsInterp.close()
    }

    // ═════════════════════════════════════════════════════════════
    // PRIVATE PIPELINE
    // ═════════════════════════════════════════════════════════════

    private fun processFrame(bitmap: Bitmap) {
        // Stage 1: YOLO detect
        val box = detectWithYolo(bitmap)
        if (box == null) {
            pushWindow(null)
            // Không thấy tiền — reset nếu cần
            val hadResult = frameWindow.any { it.isNotEmpty() }
            if (!hadResult) onResult(EMPTY_LABEL, 0f)
            computeStable()
            return
        }

        // Stage 2: crop → EfficientNet
        val crop = cropBitmap(bitmap, box)
        if (crop == null) {
            pushWindow(null)
            onResult(EMPTY_LABEL, 0f)
            return
        }

        val (label, conf) = classifyWithEfficientNet(crop)
        crop.recycle()

        if (conf >= CLASSIFY_CONF_THRESHOLD) {
            pushWindow(mapOf(label to conf))
        } else {
            pushWindow(null)
        }

        // Tính kết quả ổn định và callback
        val (stableLabel, stableConf) = computeStable()
        if (stableLabel != null) {
            onResult(stableLabel, stableConf)
        } else {
            onResult(EMPTY_LABEL, 0f)
        }
    }

    // ── Stage 1: YOLO ─────────────────────────────────────────────

    private data class BBox(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    private fun detectWithYolo(bitmap: Bitmap): BBox? {
        yoloCvs.drawBitmap(bitmap, null, Rect(0, 0, 640, 640), null)

        yoloBmp.getPixels(yoloPixels, 0, 640, 0, 0, 640, 640)
        yoloBuf.rewind()
        for (px in yoloPixels) {
            yoloBuf.putFloat(((px shr 16) and 0xFF) / 255f)
            yoloBuf.putFloat(((px shr 8)  and 0xFF) / 255f)
            yoloBuf.putFloat((px          and 0xFF) / 255f)
        }
        yoloBuf.rewind()

        val numDet = yoloInterp.getOutputTensor(0).shape()[1]
        val output = Array(1) { Array(numDet) { FloatArray(6) } }
        yoloInterp.run(yoloBuf, output)

        var bestConf = YOLO_CONF_THRESHOLD
        var bestBox: BBox? = null
        for (i in 0 until numDet) {
            val d = output[0][i]
            if (d[4] > bestConf) {
                bestConf = d[4]
                bestBox = BBox(
                    x1 = d[0].coerceIn(0f, 1f),
                    y1 = d[1].coerceIn(0f, 1f),
                    x2 = d[2].coerceIn(0f, 1f),
                    y2 = d[3].coerceIn(0f, 1f),
                )
            }
        }
        return bestBox
    }

    // ── Stage 2: EfficientNet ─────────────────────────────────────

    private fun classifyWithEfficientNet(crop: Bitmap): Pair<String, Float> {
        clsCvs.drawBitmap(crop, null, Rect(0, 0, 224, 224), null)

        clsBmp.getPixels(clsPixels, 0, 224, 0, 0, 224, 224)
        clsBuf.rewind()
        for (px in clsPixels) {
            clsBuf.putFloat((((px shr 16) and 0xFF) / 255f - 0.485f) / 0.229f)
            clsBuf.putFloat((((px shr 8)  and 0xFF) / 255f - 0.456f) / 0.224f)
            clsBuf.putFloat(((px          and 0xFF) / 255f - 0.406f) / 0.225f)
        }
        clsBuf.rewind()

        val output = Array(1) { FloatArray(labels.size) }
        clsInterp.run(clsBuf, output)

        val scores = output[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
        return Pair(labels[maxIdx], scores[maxIdx])
    }

    // ── Crop ──────────────────────────────────────────────────────

    private fun cropBitmap(src: Bitmap, box: BBox): Bitmap? {
        val x1 = (box.x1 * src.width ).toInt().coerceIn(0, src.width)
        val y1 = (box.y1 * src.height).toInt().coerceIn(0, src.height)
        val x2 = (box.x2 * src.width ).toInt().coerceIn(0, src.width)
        val y2 = (box.y2 * src.height).toInt().coerceIn(0, src.height)
        if (x2 - x1 <= 0 || y2 - y1 <= 0) return null
        return Bitmap.createBitmap(src, x1, y1, x2 - x1, y2 - y1)
    }

    // ── Frame averaging ───────────────────────────────────────────

    private fun pushWindow(scores: Map<String, Float>?) {
        frameWindow.addLast(scores ?: emptyMap())
        if (frameWindow.size > WINDOW_SIZE) frameWindow.removeFirst()
    }

    private fun computeStable(): Pair<String?, Float> {
        if (frameWindow.size < STABLE_FRAMES) return Pair(null, 0f)

        val n     = frameWindow.size.toFloat()
        val total = mutableMapOf<String, Float>()
        val count = mutableMapOf<String, Int>()

        for (frame in frameWindow) {
            for ((lbl, score) in frame) {
                total[lbl] = total.getOrDefault(lbl, 0f) + score
                count[lbl] = count.getOrDefault(lbl, 0) + 1
            }
        }

        // score = avg_conf × appearance_ratio
        val finalScores = total.mapValues { (lbl, score) ->
            (score / n) * (count[lbl]!! / n)
        }

        val best = finalScores.maxByOrNull { it.value } ?: return Pair(null, 0f)
        if (best.value < CLASSIFY_CONF_THRESHOLD * 0.5f) return Pair(null, 0f)
        if ((count[best.key] ?: 0) < STABLE_FRAMES) return Pair(null, 0f)

        val label = best.key
        val conf  = total[label]!! / count[label]!!.toFloat()
        return Pair(label, conf)
    }
}
