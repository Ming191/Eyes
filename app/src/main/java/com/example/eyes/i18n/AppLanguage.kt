package com.example.eyes.i18n

import java.util.Locale

enum class AppLanguage(
    val storageValue: String,
    val label: String,
    val nativeLabel: String,
    val ttsLocale: Locale,
    val sttLanguageTag: String
) {
    VI("vi", "Vietnamese", "Tiếng Việt", Locale.Builder().setLanguage("vi").setRegion("VN").build(), "vi-VN"),
    EN("en", "English", "English", Locale.US, "en-US");

    companion object {
        fun fromStorageValue(value: String?): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: VI
    }
}
