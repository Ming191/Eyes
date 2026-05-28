package com.example.eyes.ui.camera

import com.example.eyes.R
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider

internal class CurrencyTextMapper(
    private val localizedTextProvider: LocalizedTextProvider,
    private val languageProvider: () -> AppLanguage
) {
    fun display(label: String): String {
        return when (label) {
            "1000" -> localizedTextProvider.getString(R.string.currency_display_1000, languageProvider())
            "2000" -> localizedTextProvider.getString(R.string.currency_display_2000, languageProvider())
            "5000" -> localizedTextProvider.getString(R.string.currency_display_5000, languageProvider())
            "10000" -> localizedTextProvider.getString(R.string.currency_display_10000, languageProvider())
            "20000" -> localizedTextProvider.getString(R.string.currency_display_20000, languageProvider())
            "50000" -> localizedTextProvider.getString(R.string.currency_display_50000, languageProvider())
            "100000" -> localizedTextProvider.getString(R.string.currency_display_100000, languageProvider())
            "200000" -> localizedTextProvider.getString(R.string.currency_display_200000, languageProvider())
            "500000" -> localizedTextProvider.getString(R.string.currency_display_500000, languageProvider())
            else -> label
        }
    }

    fun spoken(label: String): String {
        return when (label) {
            "1000" -> localizedTextProvider.getString(R.string.currency_spoken_1000, languageProvider())
            "2000" -> localizedTextProvider.getString(R.string.currency_spoken_2000, languageProvider())
            "5000" -> localizedTextProvider.getString(R.string.currency_spoken_5000, languageProvider())
            "10000" -> localizedTextProvider.getString(R.string.currency_spoken_10000, languageProvider())
            "20000" -> localizedTextProvider.getString(R.string.currency_spoken_20000, languageProvider())
            "50000" -> localizedTextProvider.getString(R.string.currency_spoken_50000, languageProvider())
            "100000" -> localizedTextProvider.getString(R.string.currency_spoken_100000, languageProvider())
            "200000" -> localizedTextProvider.getString(R.string.currency_spoken_200000, languageProvider())
            "500000" -> localizedTextProvider.getString(R.string.currency_spoken_500000, languageProvider())
            else -> label
        }
    }
}
