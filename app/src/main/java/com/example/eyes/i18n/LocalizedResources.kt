package com.example.eyes.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList

fun Context.localizedFor(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(language.ttsLocale))
    }
    return createConfigurationContext(configuration)
}
