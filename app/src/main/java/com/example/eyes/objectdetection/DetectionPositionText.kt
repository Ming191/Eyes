package com.example.eyes.objectdetection

import android.content.Context
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider
import com.example.eyes.i18n.localizedFor

fun DetectionPosition.localizedText(
    context: Context,
    language: AppLanguage
): String = context.localizedFor(language).getString(labelRes)

fun DetectionPosition.localizedText(
    localizedTextProvider: LocalizedTextProvider,
    language: AppLanguage
): String = localizedTextProvider.getString(labelRes, language)
