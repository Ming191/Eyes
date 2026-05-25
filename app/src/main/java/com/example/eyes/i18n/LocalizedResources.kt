package com.example.eyes.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList

interface LocalizedTextProvider {
    val applicationContext: Context

    fun localizedContext(language: AppLanguage): Context = applicationContext.localizedFor(language)

    fun getString(resId: Int, language: AppLanguage): String =
        localizedContext(language).getString(resId)

    fun getString(resId: Int, language: AppLanguage, vararg formatArgs: Any): String =
        localizedContext(language).getString(resId, *formatArgs)

    fun getStringArray(resId: Int, language: AppLanguage): Array<String> =
        localizedContext(language).resources.getStringArray(resId)
}

class AndroidLocalizedTextProvider(
    context: Context
) : LocalizedTextProvider {
    override val applicationContext: Context = context.applicationContext
}

fun Context.localizedFor(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(language.ttsLocale))
    }
    return createConfigurationContext(configuration)
}
