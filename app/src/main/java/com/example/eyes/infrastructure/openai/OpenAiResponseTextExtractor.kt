package com.example.eyes.infrastructure.openai

import com.example.eyes.application.ports.OcrEngineRefusalException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object OpenAiResponseTextExtractor {
    fun extract(rawJson: String, json: Json): String {
        val root = json.parseToJsonElement(rawJson).jsonObject

        root["output_text"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return it.trim() }

        val outputArray = root["output"]?.jsonArray ?: JsonArray(emptyList())
        val lines = mutableListOf<String>()
        val refusals = mutableListOf<String>()

        outputArray.forEach { outputItem ->
            val contentArray = outputItem.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList())
            contentArray.forEach { contentItem ->
                val contentObject = contentItem.jsonObject
                if (contentObject["type"]?.jsonPrimitive?.contentOrNull == "refusal") {
                    contentObject["refusal"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { refusals.add(it.trim()) }
                }
                contentObject["text"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { lines.add(it.trim()) }
            }
        }

        if (lines.isEmpty() && refusals.isNotEmpty()) {
            throw OcrEngineRefusalException(refusals.joinToString("\n"))
        }

        return lines.joinToString("\n").trim()
    }
}
