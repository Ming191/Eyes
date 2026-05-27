package com.example.eyes.application.home

import android.content.Context
import com.example.eyes.R
import com.example.eyes.i18n.AppLanguage
import com.example.eyes.i18n.LocalizedTextProvider

class BuildHomeStateUseCase(
    private val localizedTextProvider: LocalizedTextProvider
) {
    operator fun invoke(language: AppLanguage): HomeState =
        localizedTextProvider.localizedContext(language).homeStateFromResources()

    private fun Context.homeStateFromResources(): HomeState = HomeState(
        welcomeTitle = getString(R.string.home_welcome_title),
        welcomeSummary = getString(R.string.home_welcome_summary),
        actions = listOf(
            HomeActionState(
                HomeActionKind.ReadTextQuick,
                getString(R.string.home_action_read_quick_title),
                getString(R.string.home_action_read_quick_description),
                getString(R.string.home_action_read_quick_supporting),
                getString(R.string.home_action_read_quick_accessibility)
            ),
            HomeActionState(
                HomeActionKind.DescribeScene,
                getString(R.string.camera_mode_scene_description_label),
                getString(R.string.camera_mode_scene_description_description),
                getString(R.string.camera_mode_scene_description_label),
                getString(R.string.camera_mode_scene_description_description)
            ),
            HomeActionState(
                HomeActionKind.DetectObjects,
                getString(R.string.camera_mode_object_detection_label),
                getString(R.string.camera_mode_object_detection_description),
                getString(R.string.camera_mode_object_detection_label),
                getString(R.string.camera_mode_object_detection_description)
            ),
            HomeActionState(
                HomeActionKind.RecognizeCurrency,
                getString(R.string.camera_mode_currency_label),
                getString(R.string.camera_mode_currency_description),
                getString(R.string.camera_mode_currency_label),
                getString(R.string.camera_mode_currency_description)
            ),
            HomeActionState(
                HomeActionKind.EmergencyCall,
                getString(R.string.home_action_emergency_title),
                getString(R.string.home_action_emergency_description),
                getString(R.string.home_action_emergency_supporting),
                getString(R.string.home_action_emergency_accessibility)
            ),
            HomeActionState(
                HomeActionKind.Voice,
                getString(R.string.home_action_voice_title),
                getString(R.string.home_action_voice_description),
                getString(R.string.home_action_voice_supporting),
                getString(R.string.home_action_voice_accessibility)
            ),
        )
    )
}
