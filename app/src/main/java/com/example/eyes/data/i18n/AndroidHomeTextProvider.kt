package com.example.eyes.data.i18n

import com.example.eyes.R
import com.example.eyes.application.home.HomeActionKind
import com.example.eyes.application.home.HomeActionState
import com.example.eyes.application.home.HomeState
import com.example.eyes.application.home.HomeTextProvider
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider

class AndroidHomeTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) : HomeTextProvider {
    override fun homeState(language: AppLanguage): HomeState = HomeState(
        welcomeTitle = text(R.string.home_welcome_title, language),
        welcomeSummary = text(R.string.home_welcome_summary, language),
        actions = listOf(
            HomeActionState(
                HomeActionKind.ReadTextQuick,
                text(R.string.home_action_read_quick_title, language),
                text(R.string.home_action_read_quick_description, language),
                text(R.string.home_action_read_quick_supporting, language),
                text(R.string.home_action_read_quick_accessibility, language)
            ),
            HomeActionState(
                HomeActionKind.DescribeScene,
                text(R.string.camera_mode_scene_description_label, language),
                text(R.string.camera_mode_scene_description_description, language),
                text(R.string.camera_mode_scene_description_label, language),
                text(R.string.camera_mode_scene_description_description, language)
            ),
            HomeActionState(
                HomeActionKind.DetectObjects,
                text(R.string.camera_mode_object_detection_label, language),
                text(R.string.camera_mode_object_detection_description, language),
                text(R.string.camera_mode_object_detection_label, language),
                text(R.string.camera_mode_object_detection_description, language)
            ),
            HomeActionState(
                HomeActionKind.RecognizeCurrency,
                text(R.string.camera_mode_currency_label, language),
                text(R.string.camera_mode_currency_description, language),
                text(R.string.camera_mode_currency_label, language),
                text(R.string.camera_mode_currency_description, language)
            ),
            HomeActionState(
                HomeActionKind.EmergencyCall,
                text(R.string.home_action_emergency_title, language),
                text(R.string.home_action_emergency_description, language),
                text(R.string.home_action_emergency_supporting, language),
                text(R.string.home_action_emergency_accessibility, language)
            ),
            HomeActionState(
                HomeActionKind.Voice,
                text(R.string.home_action_voice_title, language),
                text(R.string.home_action_voice_description, language),
                text(R.string.home_action_voice_supporting, language),
                text(R.string.home_action_voice_accessibility, language)
            ),
        )
    )

    private fun text(resId: Int, language: AppLanguage): String =
        localizedTextProvider.getString(resId, language)
}
