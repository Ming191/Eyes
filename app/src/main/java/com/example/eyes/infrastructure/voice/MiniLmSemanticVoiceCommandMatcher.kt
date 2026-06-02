package com.example.eyes.infrastructure.voice

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.eyes.application.voice.SemanticVoiceCommandMatcher
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.voice.VoiceCameraTarget
import com.example.eyes.domain.voice.VoiceIntent
import java.io.File
import kotlin.math.sqrt

class MiniLmSemanticVoiceCommandMatcher(
    private val context: Context
) : SemanticVoiceCommandMatcher {

    @Volatile
    private var disabled = false

    private val assetCopier = MiniLmModelAssetCopier(context)
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy {
        environment.createSession(modelFile().absolutePath, OrtSession.SessionOptions())
    }
    private val tokenizer: HuggingFaceTokenizer by lazy {
        HuggingFaceTokenizer.newInstance(tokenizerFile().toPath())
    }
    private val catalog: List<CatalogItem> by lazy { buildCatalog() }

    override fun match(text: String, language: AppLanguage): VoiceIntent? {
        if (disabled) return null
        if (!modelFilesReady()) return null
        return runCatching {
            val query = embed(text)
            val best = catalog.maxByOrNull { cosine(query, it.embedding) } ?: return null
            val score = cosine(query, best.embedding)
            if (score >= ACCEPT_THRESHOLD) best.intent else null
        }.onFailure { error ->
            disabled = true
            Log.w(TAG, "MiniLM semantic matcher unavailable; falling back to keyword parser", error)
        }.getOrNull()
    }

    private fun buildCatalog(): List<CatalogItem> = COMMAND_PHRASES.flatMap { (intent, phrases) ->
        phrases.map { phrase -> CatalogItem(intent, embed(phrase)) }
    }

    private fun embed(text: String): FloatArray {
        val encoding = tokenizer.encode(text)
        val ids = encoding.ids.take(MAX_TOKENS)
        val mask = encoding.attentionMask.take(MAX_TOKENS)
        val types = encoding.typeIds?.take(MAX_TOKENS).orEmpty()
        val inputIds = arrayOf(LongArray(MAX_TOKENS) { index -> ids.getOrElse(index) { PAD_ID } })
        val attentionMask = arrayOf(LongArray(MAX_TOKENS) { index -> mask.getOrElse(index) { 0L } })
        val tokenTypeIds = arrayOf(LongArray(MAX_TOKENS) { index -> types.getOrElse(index) { 0L } })
        val tensors = mutableListOf<OnnxTensor>()
        val inputs = linkedMapOf<String, OnnxTensor>()

        fun addInput(name: String, value: Array<LongArray>) {
            if (!session.inputNames.contains(name)) return
            val tensor = OnnxTensor.createTensor(environment, value)
            tensors += tensor
            inputs[name] = tensor
        }

        return try {
            addInput("input_ids", inputIds)
            addInput("attention_mask", attentionMask)
            addInput("token_type_ids", tokenTypeIds)
            session.run(inputs).use { output ->
                @Suppress("UNCHECKED_CAST")
                val hidden = output[0].value as Array<Array<FloatArray>>
                meanPoolAndNormalize(hidden[0], attentionMask[0])
            }
        } finally {
            tensors.forEach { it.close() }
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

    private fun modelFilesReady(): Boolean {
        if (modelFile().isFile && tokenizerFile().isFile) return true
        return assetCopier.copyIfAvailable(modelDir()) && modelFile().isFile && tokenizerFile().isFile
    }

    private fun modelFile(): File = File(modelDir(), MODEL_FILE)

    private fun tokenizerFile(): File = File(modelDir(), TOKENIZER_FILE)

    private fun modelDir(): File = File(context.filesDir, MODEL_DIR)

    private data class CatalogItem(val intent: VoiceIntent, val embedding: FloatArray)

    private companion object {
        private const val MODEL_DIR = "models/minilm"
        private const val MODEL_FILE = "model_int8.onnx"
        private const val TOKENIZER_FILE = "tokenizer.json"
        private const val MAX_TOKENS = 128
        private const val PAD_ID = 0L
        private const val ACCEPT_THRESHOLD = 0.80f
        private const val TAG = "MiniLmSemantic"

        private val COMMAND_PHRASES = mapOf(
            VoiceIntent.OpenCamera(VoiceCameraTarget.OCR) to listOf(
                "đọc văn bản",
                "đọc chữ trước camera",
                "đọc nội dung này",
                "read text in front of me",
                "đọc giúp tôi cái chữ này"
            ),
            VoiceIntent.OpenCamera(VoiceCameraTarget.SCENE_DESCRIPTION) to listOf(
                "mô tả khung cảnh",
                "trước mặt có gì",
                "xung quanh tôi có gì",
                "describe what is in front",
                "camera đang thấy gì",
                "nhìn xem phía trước có gì"
            ),
            VoiceIntent.OpenCamera(VoiceCameraTarget.CURRENCY) to listOf(
                "nhận diện tiền",
                "tờ tiền này bao nhiêu",
                "xem mệnh giá tờ tiền",
                "how much is this bill",
                "đây là tờ bao nhiêu",
                "xem hộ tờ này bao nhiêu tiền"
            ),
            VoiceIntent.OpenCamera(VoiceCameraTarget.OBJECT_DETECTION) to listOf(
                "nhận diện vật thể",
                "vật thể trước mặt",
                "có vật gì",
                "phát hiện đồ vật",
                "detect objects",
                "object detection"
            ),
            VoiceIntent.OpenSettings to listOf("mở cài đặt", "cài đặt", "vào cài đặt", "thiết lập", "settings", "open settings"),
            VoiceIntent.OpenHome to listOf("về trang chủ", "mở trang chủ", "trang chủ", "home", "go home"),
            VoiceIntent.OpenEmergencyList to listOf("gọi khẩn cấp", "mở gọi khẩn cấp", "khẩn cấp", "emergency call", "open emergency"),
            VoiceIntent.Repeat to listOf("đọc lại", "nói lại câu vừa rồi", "repeat last response", "nói lại giúp tôi", "nhắc lại câu vừa rồi"),
            VoiceIntent.Help to listOf("tôi nói được gì", "hướng dẫn lệnh", "help", "giúp đỡ", "có thể nói lệnh gì")
        )
    }
}
