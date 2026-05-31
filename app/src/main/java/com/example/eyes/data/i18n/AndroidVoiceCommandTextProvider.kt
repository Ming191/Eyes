package com.example.eyes.data.i18n

import com.example.eyes.R
import com.example.eyes.application.voice.VoiceCommandText
import com.example.eyes.application.voice.VoiceCommandTextProvider
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.i18n.LocalizedTextProvider

class AndroidVoiceCommandTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) : VoiceCommandTextProvider {
    override fun text(language: AppLanguage): VoiceCommandText = VoiceCommandText(
        readText = localizedTextProvider.getString(R.string.voice_vm_read_text_ack, language),
        describeScene = localizedTextProvider.getString(R.string.voice_vm_describe_scene_ack, language),
        recognizeCurrency = localizedTextProvider.getString(R.string.voice_vm_recognize_currency_ack, language),
        detectObjects = localizedTextProvider.getString(R.string.voice_vm_detect_objects_ack, language),
        openHome = localizedTextProvider.getString(R.string.voice_vm_open_home_ack, language),
        openSettings = localizedTextProvider.getString(R.string.voice_vm_open_settings_ack, language),
        openEmergency = localizedTextProvider.getString(R.string.voice_vm_open_emergency_ack, language),
        ocrQuick = localizedTextProvider.getString(R.string.voice_vm_ocr_quick_ack, language),
        ocrAccurate = localizedTextProvider.getString(R.string.voice_vm_ocr_accurate_ack, language),
        stop = localizedTextProvider.getString(R.string.voice_vm_stopped_ack, language),
        nothingToRepeat = localizedTextProvider.getString(R.string.voice_vm_nothing_to_repeat, language),
        help = localizedTextProvider.getString(R.string.voice_vm_help_text, language),
        unknown = localizedTextProvider.getString(R.string.voice_vm_unknown_command, language)
    )
}
