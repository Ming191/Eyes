package com.example.eyes.map

sealed interface MapUiState {
    data object Idle : MapUiState
    data object LocationPermissionRequired : MapUiState
    data object Locating : MapUiState
    data class Ready(val location: UserLocation) : MapUiState
    data class RouteLoading(val location: UserLocation, val destination: String) : MapUiState
    data class RouteReady(
        val location: UserLocation,
        val destination: String,
        val route: RoutePreview
    ) : MapUiState
    data class Error(val message: String) : MapUiState
}

data class UserLocation(
    val latitude: Double,
    val longitude: Double
)

data class RoutePreview(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<RouteStep>
)

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val durationSeconds: Int
)
