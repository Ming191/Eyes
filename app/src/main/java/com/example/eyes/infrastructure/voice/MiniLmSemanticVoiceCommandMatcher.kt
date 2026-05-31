package com.example.eyes.infrastructure.voice

import android.content.Context
import android.util.Log
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import com.example.eyes.application.voice.SemanticVoiceCommandMatcher
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.voice.VoiceCommand
import java.io.File
import kotlin.math.sqrt
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

class MiniLmSemanticVoiceCommandMatcher(
    private val context: Context
) : SemanticVoiceCommandMatcher {

    @Volatile
    private var disabled = false

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy {
        environment.createSession(modelFile().absolutePath, OrtSession.SessionOptions())
    }
    private val tokenizer: HuggingFaceTokenizer by lazy {
        HuggingFaceTokenizer.newInstance(tokenizerFile().toPath())
    }
    private val catalog: List<CatalogItem> by lazy { buildCatalog() }

    override fun match(text: String, language: AppLanguage): VoiceCommand? {
        if (disabled) return null
        if (!modelFilesReady()) return null
        return runCatching {
            val query = embed(text)
            val best = catalog.maxByOrNull { cosine(query, it.embedding) } ?: return null
            val score = cosine(query, best.embedding)
            if (score >= ACCEPT_THRESHOLD) best.command else null
        }.onFailure { error ->
            disabled = true
            Log.w(TAG, "MiniLM semantic matcher unavailable; falling back to keyword parser", error)
        }.getOrNull()
    }

    private fun buildCatalog(): List<CatalogItem> = COMMAND_PHRASES.flatMap { (command, phrases) ->
        phrases.map { phrase -> CatalogItem(command, embed(phrase)) }
    }

    private fun embed(text: String): FloatArray {
        val encoding = tokenizer.encode(text)
        val ids = encoding.ids.map { it.toLong() }.take(MAX_TOKENS)
        val mask = encoding.attentionMask.map { it.toLong() }.take(MAX_TOKENS)
        val inputIds = arrayOf(LongArray(MAX_TOKENS) { index -> ids.getOrElse(index) { PAD_ID } })
        val attentionMask = arrayOf(LongArray(MAX_TOKENS) { index -> mask.getOrElse(index) { 0L } })

        OnnxTensor.createTensor(environment, inputIds).use { idsTensor ->
            OnnxTensor.createTensor(environment, attentionMask).use { maskTensor ->
                val result = session.run(mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor))
                result.use { output ->
                    @Suppress("UNCHECKED_CAST")
                    val hidden = output[0].value as Array<Array<FloatArray>>
                    return meanPoolAndNormalize(hidden[0], attentionMask[0])
                }
            }
        }
    }

    private fun meanPoolAndNormalize(tokens: Array<FloatArray>, mask: LongArray): FloatArray {
        val sum = FloatArray(tokens.first().size)
        var count = 0f
        tokens.forEachIndexed { index, values ->
            if (mask.getOrElse(index) { 0L } == 1L) {
                values.forEachIndexed { dim, value -> sum[dim] += value }
                count += 1f
            }
        }
        if (count <= 0f) return sum
        var norm = 0f
        sum.indices.forEach { index ->
            sum[index] /= count
            norm += sum[index] * sum[index]
        }
        val scale = sqrt(norm.coerceAtLeast(1e-12f))
        sum.indices.forEach { index -> sum[index] /= scale }
        return sum
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        a.indices.forEach { index -> dot += a[index] * b[index] }
        return dot
    }

    private fun modelFilesReady(): Boolean = modelFile().isFile && tokenizerFile().isFile

    private fun modelFile(): File = File(modelDir(), MODEL_FILE)

    private fun tokenizerFile(): File = File(modelDir(), TOKENIZER_FILE)

    private fun modelDir(): File = File(context.filesDir, MODEL_DIR)

    private data class CatalogItem(val command: VoiceCommand, val embedding: FloatArray)

    private companion object {
        private const val MODEL_DIR = "models/minilm"
        private const val MODEL_FILE = "model_int8.onnx"
        private const val TOKENIZER_FILE = "tokenizer.json"
        private const val MAX_TOKENS = 128
        private const val PAD_ID = 0L
        private const val ACCEPT_THRESHOLD = 0.80f
        private const val TAG = "MiniLmSemantic"

        private val COMMAND_PHRASES = mapOf(
            VoiceCommand.ReadText to listOf("đọc văn bản", "đọc chữ trước camera", "đọc nội dung này", "read text in front of me", "đọc giúp tôi cái chữ này"),
            VoiceCommand.DescribeScene to listOf("mô tả khung cảnh", "trước mặt có gì", "xung quanh tôi có gì", "describe what is in front", "camera đang thấy gì", "nhìn xem phía trước có gì"),
            VoiceCommand.RecognizeCurrency to listOf("nhận diện tiền", "tờ tiền này bao nhiêu", "xem mệnh giá tờ tiền", "how much is this bill", "đây là tờ bao nhiêu", "xem hộ tờ này bao nhiêu tiền"),
            VoiceCommand.DetectObjects to listOf("nhận diện vật thể", "vật thể trước mặt", "có vật gì", "phát hiện đồ vật", "detect objects", "object detection"),
            VoiceCommand.OcrQuick to listOf("đọc nhanh", "ocr nhanh", "chế độ nhanh", "đọc văn bản nhanh", "quick ocr", "read quickly"),
            VoiceCommand.OcrAccurate to listOf("đọc chính xác", "ocr chính xác", "ocr kỹ", "đọc văn bản chính xác", "accurate ocr", "read accurately"),
            VoiceCommand.OpenSettings to listOf("mở cài đặt", "cài đặt", "vào cài đặt", "thiết lập", "settings", "open settings"),
            VoiceCommand.OpenHome to listOf("về trang chủ", "mở trang chủ", "trang chủ", "home", "go home"),
            VoiceCommand.OpenEmergency to listOf("gọi khẩn cấp", "mở gọi khẩn cấp", "khẩn cấp", "emergency call", "open emergency"),
            VoiceCommand.Repeat to listOf("đọc lại", "nói lại câu vừa rồi", "repeat last response", "nói lại giúp tôi", "nhắc lại câu vừa rồi"),
            VoiceCommand.Help to listOf("tôi nói được gì", "hướng dẫn lệnh", "help", "giúp đỡ", "có thể nói lệnh gì")
        )
    }
}
