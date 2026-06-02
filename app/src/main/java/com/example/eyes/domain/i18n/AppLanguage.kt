package com.example.eyes.domain.i18n

import java.util.Locale

const val VOICE_INPUT_LANGUAGE_TAG = "vi-VN"

enum class AppLanguage(
    val storageValue: String,
    val label: String,
    val nativeLabel: String,
    val ttsLocale: Locale,
    val sttLanguageTag: String
) {
    VI("vi", "Tiếng Việt", "Tiếng Việt", Locale.Builder().setLanguage("vi").setRegion("VN").build(), VOICE_INPUT_LANGUAGE_TAG),
    EN("en", "Tiếng Anh", "English", Locale.US, VOICE_INPUT_LANGUAGE_TAG);

    companion object {
        fun fromStorageValue(value: String?): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: VI

        fun fromLocale(locale: Locale): AppLanguage = when (locale.language.lowercase(Locale.ROOT)) {
            EN.storageValue -> EN
            else -> VI
        }
    }
}
