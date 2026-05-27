package com.example.eyes.data.i18n

import com.example.eyes.R
import com.example.eyes.application.home.HomeAnnouncementTextProvider
import com.example.eyes.application.navigation.DestinationAnnouncementTextProvider
import com.example.eyes.application.voice.VoiceCommandText
import com.example.eyes.application.voice.VoiceCommandTextProvider
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.navigation.Destination
import com.example.eyes.i18n.LocalizedTextProvider

class AndroidHomeAnnouncementTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) : HomeAnnouncementTextProvider {
    override fun greeting(language: AppLanguage): String =
        localizedTextProvider.getString(R.string.home_greeting, language)
}

class AndroidDestinationAnnouncementTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) : DestinationAnnouncementTextProvider {
    override fun intro(destination: Destination, language: AppLanguage): String {
        val textRes = when (destination) {
            Destination.HOME -> R.string.voice_guide_home_intro
            Destination.CAMERA -> R.string.voice_guide_camera_intro
            Destination.SETTINGS -> R.string.voice_guide_settings_intro
        }
        return localizedTextProvider.getString(textRes, language)
    }
}

class AndroidVoiceCommandTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) : VoiceCommandTextProvider {
    override fun text(language: AppLanguage): VoiceCommandText = VoiceCommandText(
        readText = localizedTextProvider.getString(R.string.voice_vm_read_text_ack, language),
        describeScene = localizedTextProvider.getString(R.string.voice_vm_describe_scene_ack, language),
        recognizeCurrency = localizedTextProvider.getString(R.string.voice_vm_recognize_currency_ack, language),
        nothingToRepeat = localizedTextProvider.getString(R.string.voice_vm_nothing_to_repeat, language),
        stopped = localizedTextProvider.getString(R.string.voice_vm_stopped_ack, language),
        help = localizedTextProvider.getString(R.string.voice_vm_help_text, language),
        unknown = localizedTextProvider.getString(R.string.voice_vm_unknown_command, language)
    )
}
