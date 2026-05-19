package com.example.eyes.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data object CameraRoute

@Serializable
data object MapRoute

@Serializable
data object SettingsRoute

@Serializable
data object VoiceRoute

enum class TopLevelDestination(
    val title: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(
        title = "SoundVision",
        label = "Trang chủ",
        icon = Icons.Rounded.Home
    ),
    CAMERA(
        title = "Camera trợ lý",
        label = "Camera",
        icon = Icons.Rounded.PhotoCamera
    ),
    MAP(
        title = "Dẫn đường",
        label = "Bản đồ",
        icon = Icons.Rounded.Map
    ),
    SETTINGS(
        title = "Cài đặt",
        label = "Cài đặt",
        icon = Icons.Rounded.Tune
    )
}