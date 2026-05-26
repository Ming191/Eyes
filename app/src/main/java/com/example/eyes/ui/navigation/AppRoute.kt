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

enum class TopLevelDestination(
    @StringRes val titleRes: Int,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    HOME(
        titleRes = R.string.nav_title_home,
        labelRes = R.string.nav_label_home,
        icon = Icons.Rounded.Home
    ),
    CAMERA(
        titleRes = R.string.nav_title_camera,
        labelRes = R.string.nav_label_camera,
        icon = Icons.Rounded.PhotoCamera
    ),
    SETTINGS(
        titleRes = R.string.nav_title_settings,
        labelRes = R.string.nav_label_settings,
        icon = Icons.Rounded.Tune
    )
}
