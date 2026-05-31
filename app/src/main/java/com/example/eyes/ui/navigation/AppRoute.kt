package com.example.eyes.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tune
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.eyes.R
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data object CameraRoute

@Serializable
data object SettingsRoute

@Serializable
data object EmergencyRoute

enum class TopLevelDestination(
    @param:StringRes val titleRes: Int,
    @param:StringRes val labelRes: Int,
    @param:StringRes val announcementRes: Int,
    val icon: ImageVector
) {
    HOME(
        titleRes = R.string.nav_title_home,
        labelRes = R.string.nav_label_home,
        announcementRes = R.string.voice_guide_home_intro,
        icon = Icons.Rounded.Home
    ),
    CAMERA(
        titleRes = R.string.nav_title_camera,
        labelRes = R.string.nav_label_camera,
        announcementRes = R.string.voice_guide_camera_intro,
        icon = Icons.Rounded.PhotoCamera
    ),
    SETTINGS(
        titleRes = R.string.nav_title_settings,
        labelRes = R.string.nav_label_settings,
        announcementRes = R.string.voice_guide_settings_intro,
        icon = Icons.Rounded.Tune
    )
}
