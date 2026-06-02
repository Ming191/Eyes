package com.example.eyes.infrastructure.ocr

import android.content.Context
import android.os.Environment
import com.example.eyes.domain.ocr.OcrMode
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrExperimentLogEntry(
    val timestampMs: Long,
    val mode: OcrMode,
    val processingTimeMs: Long,
    val recognizedText: String,
    val usedFallback: Boolean,
    val fallbackReason: String?,
    val translationFailed: Boolean,
    val error: String?
)

class OcrExperimentLogger(
    private val context: Context
) {
    private val lock = Any()

    val resultFile: File
        get() {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                .resolve(EXPERIMENTS_DIRECTORY)
            return File(directory, RESULT_FILE_NAME)
        }

    suspend fun append(entry: OcrExperimentLogEntry) {
        withContext(Dispatchers.IO) {
            val file = resultFile
            file.parentFile?.mkdirs()

            val line = listOf(
                Instant.ofEpochMilli(entry.timestampMs).toString(),
                entry.timestampMs.toString(),
                entry.mode.name.lowercase(),
                entry.processingTimeMs.toString(),
                entry.usedFallback.toString(),
                entry.fallbackReason.orEmpty(),
                entry.translationFailed.toString(),
                entry.error.orEmpty(),
                entry.recognizedText
            ).joinToString(separator = ",") { it.toCsvCell() }

            synchronized(lock) {
                if (!file.exists()) {
                    file.writeText(CSV_HEADER, Charsets.UTF_8)
                }
                file.appendText("$line\n", Charsets.UTF_8)
            }
        }
    }

    private fun String.toCsvCell(): String {
        val normalized = replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
            .replace("\"", "\"\"")
        return "\"$normalized\""
    }

    private companion object {
        const val EXPERIMENTS_DIRECTORY = "experiments"
        const val RESULT_FILE_NAME = "ocr_results_runtime.csv"
        const val CSV_HEADER =
            "timestamp_iso,timestamp_ms,mode,processing_time_ms,used_fallback,fallback_reason,translation_failed,error,recognized_text\n"
    }
}
