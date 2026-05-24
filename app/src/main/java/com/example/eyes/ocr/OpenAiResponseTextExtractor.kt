package com.example.eyes.ocr

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

        outputArray.forEach { outputItem ->
            val contentArray = outputItem.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList())
            contentArray.forEach { contentItem ->
                contentItem.jsonObject["text"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { lines.add(it.trim()) }
            }
        }

        return lines.joinToString("\n").trim()
    }
}
